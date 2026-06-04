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

private val IDENTIFIER_REGEX = Regex("[A-Za-z_][A-Za-z0-9_]*(\\.[A-Za-z_][A-Za-z0-9_]*)?")

private data class Migration(
    val path: String,
    val body: String,
    val checksum: String
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
    private val liquibaseChangelogTable: String? = "databasechangelog",
    private val metadataTable: SqiffyMetadataTable = SqiffyMetadataTable(),
    private val resourceLoader: (path: String) -> String? = ::classpathResource,
) : Migrator<List<String>> {

    override fun runMigrations(database: SqiffyDatabase): List<String> {
        val history = ChangelogHistory(database, metadataTable)
        val jdbi = database.getJdbi()

        jdbi.useHandle<Exception> { history.ensureTable(it) }

        val applied = jdbi.withHandle<MutableMap<String, String>, Exception> { history.loadApplied(it) }

        val migrations = ChangelogIndex.read(indexPath, resourceLoader).map { path ->
            val body = normalizeLineEndings(
                resourceLoader(path)
                    ?: throw MigrationException("Migration script listed in $indexPath not found on classpath: $path")
            )
            Migration(path = path, body = body, checksum = sha256(body))
        }

        database.logger.log(Level.INFO, "Found ${migrations.size} migrations in $indexPath")

        if (applied.isEmpty() && liquibaseChangelogTable != null) {
            importLiquibaseState(database, history, migrations, liquibaseChangelogTable)
                .forEach { applied[it.path] = it.checksum }
        }

        val result = mutableListOf<String>()

        for (migration in migrations) {
            val recordedChecksum = applied[migration.path]

            if (recordedChecksum != null) {
                verifyChecksum(database, migration, recordedChecksum)
                continue
            }

            database.logger.log(Level.INFO, "Applying migration: ${migration.path}")
            applyMigration(database, history, migration)
            result.add(migration.path)
        }

        if (result.isEmpty()) {
            database.logger.log(Level.INFO, "Database schema is up to date")
        } else {
            database.logger.log(Level.INFO, "Applied ${result.size} migrations: ${result.joinToString(", ")}")
        }

        return result
    }

    private fun applyMigration(database: SqiffyDatabase, history: ChangelogHistory, migration: Migration) {
        val jdbi = database.getJdbi()

        try {
            try {
                jdbi.useTransaction<Exception> { handle -> executeAndRecord(database, history, handle, migration) }
            } catch (exception: Exception) {
                if (!isTransactionBlockError(exception)) {
                    throw exception
                }

                database.logger.log(Level.WARN, "Migration ${migration.path} cannot run inside a transaction block, retrying with autocommit")

                jdbi.useHandle<Exception> { handle ->
                    val connection = handle.connection
                    val previousAutoCommit = connection.autoCommit
                    connection.autoCommit = true
                    try {
                        executeAndRecord(database, history, handle, migration)
                    } finally {
                        connection.autoCommit = previousAutoCommit
                    }
                }
            }
        } catch (exception: Exception) {
            throw MigrationException("Failed to apply migration: ${migration.path}", exception)
        }
    }

    private fun executeAndRecord(database: SqiffyDatabase, history: ChangelogHistory, handle: Handle, migration: Migration) {
        executeBody(database, handle, migration.body)
        history.record(handle, migration.path, migration.checksum)
    }

    private fun executeBody(database: SqiffyDatabase, handle: Handle, body: String) {
        // Postgres runs a whole multi-statement body (incl. dollar-quoted blocks) in one call; other
        // drivers don't (SQLite would run only the first statement), so split into statements there.
        if (database.getDialect() == Dialect.POSTGRESQL) {
            handle.connection.createStatement().use { it.execute(body) }
        } else {
            handle.createScript(body).execute()
        }
    }

    private fun verifyChecksum(database: SqiffyDatabase, migration: Migration, recordedChecksum: String) {
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
        database: SqiffyDatabase,
        history: ChangelogHistory,
        migrations: List<Migration>,
        tableName: String
    ): List<Migration> {
        // interpolated into SQL below (table identifiers can't be bound as parameters), so keep it an identifier
        require(tableName.matches(IDENTIFIER_REGEX)) { "Invalid Liquibase changelog table name: '$tableName'" }
        val jdbi = database.getJdbi()

        val liquibaseFilenames = jdbi.withHandle<List<String>?, Exception> { handle ->
            if (!tableExists(handle, tableName)) {
                null
            } else {
                handle.createQuery("SELECT filename FROM $tableName")
                    .mapTo(String::class.java)
                    .list()
            }
        } ?: return emptyList()

        if (liquibaseFilenames.isEmpty()) {
            return emptyList()
        }

        database.logger.log(
            Level.INFO,
            "Detected Liquibase changelog table '$tableName' with ${liquibaseFilenames.size} applied changesets, importing state"
        )

        val imported = mutableListOf<Migration>()

        jdbi.useTransaction<Exception> { handle ->
            migrations.forEach { migration ->
                if (liquibaseFilenames.any { liquibaseMatches(migration.path, it) }) {
                    history.record(handle, migration.path, migration.checksum)
                    imported.add(migration)
                }
            }
        }

        database.logger.log(Level.INFO, "Imported ${imported.size} changesets from Liquibase state")
        return imported
    }

    private fun tableExists(handle: Handle, qualifiedName: String): Boolean {
        // JDBC wants schema and table separately, so split "schema.table" before probing
        val schema = qualifiedName.substringBefore('.', "").ifBlank { null }
        val table = qualifiedName.substringAfter('.')
        val metaData = handle.connection.metaData

        for (schemaCandidate in linkedSetOf(schema, schema?.lowercase(), schema?.uppercase())) {
            for (tableCandidate in linkedSetOf(table, table.lowercase(), table.uppercase())) {
                metaData.getTables(null, schemaCandidate, tableCandidate, arrayOf("TABLE")).use { resultSet ->
                    if (resultSet.next()) {
                        return true
                    }
                }
            }
        }

        return false
    }

    private fun liquibaseMatches(resolvedPath: String, liquibaseFilename: String): Boolean {
        val path = resolvedPath.trimStart('/')
        val filename = liquibaseFilename.replace('\\', '/').trimStart('/')
        return path == filename || path.endsWith("/$filename") || filename.endsWith("/$path")
    }

    private fun isTransactionBlockError(throwable: Throwable?): Boolean {
        var cause = throwable
        while (cause != null) {
            if (cause.message?.contains("cannot run inside a transaction block", ignoreCase = true) == true) {
                return true
            }
            cause = cause.cause
        }
        return false
    }

}
