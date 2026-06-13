package com.dzikoysk.sqiffy.migrator

import com.dzikoysk.sqiffy.SqiffyDatabase
import com.dzikoysk.sqiffy.dialect.Dialect
import org.jdbi.v3.core.Handle
import org.slf4j.event.Level

enum class ChecksumPolicy {
    FAIL,
    WARN,
    IGNORE
}

class MigrationException(message: String, cause: Throwable? = null) : RuntimeException(message, cause)

private const val NON_TRANSACTIONAL_MARKER = "-- sqiffy: no-transaction"

private data class Migration(
    val path: String,
    val body: String,
    val checksum: String,
    val transactional: Boolean
)

/**
 * Applies plain `.sql` scripts listed by a [changelog index][ChangelogIndex] file, tracking applied
 * scripts with a checksum in the shared `sqiffy_metadata` history. Adopts an existing
 * Liquibase-managed database when [liquibaseChangelogTable] is set. See the migrations guide for
 * dialect and concurrency caveats.
 */
class FileMigrator(
    private val indexPath: String,
    private val checksumPolicy: ChecksumPolicy = ChecksumPolicy.FAIL,
    private val liquibaseChangelogTable: String? = null,
    private val metadataTable: SqiffyMetadataTable = SqiffyMetadataTable(),
    private val resourceLoader: (path: String) -> String? = ::classpathResource,
) : Migrator<List<String>> {

    override fun runMigrations(database: SqiffyDatabase<*>): List<String> {
        val history = ChangelogHistory(database, metadataTable)
        val jdbi = database.getJdbi()

        jdbi.useHandle<Exception> {
            history.ensureTable(it)
        }

        val applied = jdbi.withHandle<MutableMap<String, String>, Exception> {
            history.loadApplied(it)
        }

        val migrations = ChangelogIndex.read(indexPath, resourceLoader).map { path ->
            val body = normalizeLineEndings(
                resourceLoader(path) ?: throw MigrationException("Migration script listed in $indexPath not found on classpath: $path")
            )
            Migration(
                path = path,
                body = body,
                checksum = sha256(body),
                transactional = body.lineSequence().none { it.trim().equals(NON_TRANSACTIONAL_MARKER, ignoreCase = true) }
            )
        }

        database.logger.log(Level.INFO, "Found ${migrations.size} migrations in $indexPath")

        if (applied.isEmpty() && liquibaseChangelogTable != null) {
            importLiquibaseState(database = database, history = history, migrations = migrations, tableName = liquibaseChangelogTable).forEach {
                applied[it.path] = it.checksum
            }
        }

        val result = mutableListOf<String>()

        for (migration in migrations) {
            val recordedChecksum = applied[migration.path]

            if (recordedChecksum != null) {
                verifyChecksum(database = database, migration = migration, recordedChecksum = recordedChecksum)
                continue
            }

            database.logger.log(Level.INFO, "Applying migration: ${migration.path}")
            applyMigration(database = database, history = history, migration = migration)
            result.add(migration.path)
        }

        if (result.isEmpty()) {
            database.logger.log(Level.INFO, "Database schema is up to date (${migrations.size} migrations applied)")
        } else {
            database.logger.log(Level.INFO, "Applied ${result.size} of ${migrations.size} migrations: ${result.joinToString(", ")}")
        }

        return result
    }

    private fun applyMigration(database: SqiffyDatabase<*>, history: ChangelogHistory, migration: Migration) {
        val jdbi = database.getJdbi()
        try {
            if (migration.transactional) {
                jdbi.useTransaction<Exception> {
                    executeAndRecord(database = database, history = history, handle = it, migration = migration)
                }
            } else {
                // opted out via the no-transaction marker (e.g. CREATE INDEX CONCURRENTLY): run in autocommit
                jdbi.useHandle<Exception> {
                    val connection = it.connection
                    val previousAutoCommit = connection.autoCommit
                    connection.autoCommit = true
                    try {
                        executeAndRecord(database = database, history = history, handle = it, migration = migration)
                    } finally {
                        connection.autoCommit = previousAutoCommit
                    }
                }
            }
        } catch (exception: Exception) {
            throw MigrationException("Failed to apply migration: ${migration.path}", exception)
        }
    }

    private fun executeAndRecord(database: SqiffyDatabase<*>, history: ChangelogHistory, handle: Handle, migration: Migration) {
        executeBody(database = database, handle = handle, body = migration.body)
        history.record(handle = handle, path = migration.path, checksum = migration.checksum)
    }

    private fun executeBody(database: SqiffyDatabase<*>, handle: Handle, body: String) {
        // Postgres runs a whole multi-statement body (incl. dollar-quoted blocks) in one call; other
        // drivers don't (SQLite would run only the first statement), so split into statements there.
        if (database.getDialect() == Dialect.POSTGRESQL) {
            handle.connection.createStatement().use { it.execute(body) }
        } else {
            handle.createScript(body).execute()
        }
    }

    private fun verifyChecksum(database: SqiffyDatabase<*>, migration: Migration, recordedChecksum: String) {
        if (migration.checksum == recordedChecksum || recordedChecksum.isEmpty()) {
            return
        }

        val message = "Checksum mismatch for already-applied migration ${migration.path}: " +
            "recorded=$recordedChecksum current=${migration.checksum}. The script changed after it was applied."

        when (checksumPolicy) {
            ChecksumPolicy.FAIL -> throw MigrationException(message)
            ChecksumPolicy.WARN -> database.logger.log(Level.WARN, message)
            ChecksumPolicy.IGNORE -> {}
        }
    }

    private fun importLiquibaseState(
        database: SqiffyDatabase<*>,
        history: ChangelogHistory,
        migrations: List<Migration>,
        tableName: String
    ): List<Migration> {
        val jdbi = database.getJdbi()

        val liquibaseFilenames = jdbi.withHandle<List<String>, Exception> { handle ->
            when {
                tableExists(handle, tableName) -> handle.createQuery("SELECT filename FROM $tableName").mapTo(String::class.java).list()
                else -> emptyList()
            }
        }

        if (liquibaseFilenames.isEmpty()) {
            return emptyList()
        }

        database.logger.log(
            Level.INFO,
            "Detected Liquibase changelog table '$tableName' with ${liquibaseFilenames.size} applied changesets, importing state"
        )

        val imported = migrations.filter { migration ->
            liquibaseFilenames.any {
                liquibaseMatches(resolvedPath = migration.path, liquibaseFilename = it)
            }
        }

        jdbi.useTransaction<Exception> { handle ->
            imported.forEach { history.record(handle, it.path, it.checksum) }
        }

        database.logger.log(Level.INFO, "Imported ${imported.size} changesets from Liquibase state")
        return imported
    }

    private fun tableExists(handle: Handle, qualifiedName: String): Boolean {
        // JDBC wants schema and table separately, so split "schema.table" before probing
        val schema = qualifiedName.substringBefore('.', "").ifBlank { null }
        val table = qualifiedName.substringAfter('.')
        val metaData = handle.connection.metaData

        return linkedSetOf(schema, schema?.lowercase(), schema?.uppercase()).any { schemaCandidate ->
            linkedSetOf(table, table.lowercase(), table.uppercase()).any { tableCandidate ->
                metaData.getTables(null, schemaCandidate, tableCandidate, arrayOf("TABLE")).use { it.next() }
            }
        }
    }

    private fun liquibaseMatches(resolvedPath: String, liquibaseFilename: String): Boolean {
        val path = resolvedPath.trimStart('/')
        val filename = liquibaseFilename.replace('\\', '/').trimStart('/')
        return path == filename || path.endsWith("/$filename") || filename.endsWith("/$path")
    }

}
