package com.dzikoysk.sqiffy.migrator

import com.dzikoysk.sqiffy.SqiffyDatabase
import org.jdbi.v3.core.Handle
import org.slf4j.event.Level

/** How the migrator reacts when an already-applied script's content no longer matches its stored checksum. */
enum class ChecksumPolicy {
    /** Abort the migration (default) — the safest behaviour, mirrors Liquibase. */
    FAIL,
    /** Log a warning and continue, treating the changeset as already applied. */
    WARN,
    /** Silently continue. */
    IGNORE
}

/** Where an entry in the ledger came from during a run. */
enum class ChangesetSource {
    /** The script was executed during this run. */
    APPLIED,
    /** The script was recorded as already-applied by importing pre-existing Liquibase state. */
    IMPORTED
}

data class AppliedChangeset(
    val path: String,
    val checksum: String,
    val source: ChangesetSource
)

/**
 * Configures the one-time import of state from a database previously managed by Liquibase.
 *
 * On the first run (when the Sqiffy ledger holds no changesets yet) the migrator reads the
 * Liquibase tracking table and marks every migration whose script matches an already-applied
 * Liquibase `filename` as applied — so the existing schema is adopted without re-executing
 * anything. After that first run the Liquibase table is left untouched and ignored.
 */
data class LiquibaseImport(
    val tableName: String = "databasechangelog"
) {
    init {
        // The tracking table name is interpolated into a raw SQL query (table identifiers can't be bound
        // as JDBC parameters), so constrain it to a plain, optionally schema-qualified identifier.
        require(tableName.matches(IDENTIFIER_REGEX)) { "Invalid Liquibase changelog table name: '$tableName'" }
    }

    private companion object {
        val IDENTIFIER_REGEX = Regex("[A-Za-z_][A-Za-z0-9_]*(\\.[A-Za-z_][A-Za-z0-9_]*)?")
    }
}

class MigrationException(message: String, cause: Throwable? = null) : RuntimeException(message, cause)

private data class Migration(
    val path: String,
    val body: String,
    val checksum: String
)

/**
 * A file-based, Liquibase-free database migrator.
 *
 * Migrations are plain `.sql` scripts listed, in order, by a [changelog index][ChangelogIndex]
 * file on the classpath. Each script is one changeset: its whole body is executed as a single
 * statement (so dollar-quoted PL/pgSQL and multi-statement scripts work as-is) inside its own
 * transaction, and recorded in the shared `sqiffy_metadata` ledger keyed by its resolved path
 * together with a SHA-256 checksum.
 *
 * Re-runs are idempotent: already-recorded scripts are skipped (and, depending on
 * [checksumPolicy], checked for drift). Existing Liquibase-managed databases can be adopted
 * transparently via [liquibaseImport].
 *
 * Note: executing DDL inside a transaction requires PostgreSQL 12+ for statements such as
 * `ALTER TYPE ... ADD VALUE`; the migrator automatically retries a script outside a transaction
 * if the database reports it "cannot run inside a transaction block".
 *
 * Note: each script's whole body is sent to the driver as a single statement, which relies on the
 * database executing multi-statement strings. PostgreSQL does (including dollar-quoted bodies);
 * SQLite executes only the first statement and MySQL needs `allowMultiQueries=true`. On those
 * dialects keep one statement per script.
 *
 * Note: the migrator does not take a distributed lock, so callers must serialize migrations — run
 * them once at startup with a single instance migrating before others come up (e.g. a rolling
 * release where the new instance becomes healthy before the next starts). Two instances racing an
 * empty database fail safe (the loser aborts with [MigrationException]) but do not coordinate.
 */
class FileMigrator(
    private val indexPath: String,
    private val checksumPolicy: ChecksumPolicy = ChecksumPolicy.FAIL,
    private val liquibaseImport: LiquibaseImport? = LiquibaseImport(),
    private val metadataTable: SqiffyMetadataTable = SqiffyMetadataTable(),
    private val resourceLoader: ResourceLoader = ClasspathResourceLoader(),
) : Migrator<List<AppliedChangeset>> {

    override fun runMigrations(database: SqiffyDatabase): List<AppliedChangeset> {
        val ledger = ChangelogLedger(database, metadataTable)
        val jdbi = database.getJdbi()

        jdbi.useHandle<Exception> { ledger.ensureTable(it) }

        val applied = jdbi.withHandle<MutableMap<String, String>, Exception> { ledger.loadApplied(it) }

        val migrations = ChangelogIndex.read(indexPath, resourceLoader).map { path ->
            val body = normalizeLineEndings(
                resourceLoader.readText(path)
                    ?: throw MigrationException("Migration script listed in $indexPath not found on classpath: $path")
            )
            Migration(path = path, body = body, checksum = sha256(body))
        }

        database.logger.log(Level.INFO, "Found ${migrations.size} migrations in $indexPath")

        if (applied.isEmpty() && liquibaseImport != null) {
            importLiquibaseState(database, ledger, migrations, liquibaseImport)
                .forEach { applied[it.path] = it.checksum }
        }

        val result = mutableListOf<AppliedChangeset>()

        for (migration in migrations) {
            val recordedChecksum = applied[migration.path]

            if (recordedChecksum != null) {
                verifyChecksum(database, migration, recordedChecksum)
                continue
            }

            database.logger.log(Level.INFO, "Applying migration: ${migration.path}")
            applyMigration(database, ledger, migration)
            result.add(AppliedChangeset(migration.path, migration.checksum, ChangesetSource.APPLIED))
        }

        if (result.isEmpty()) {
            database.logger.log(Level.INFO, "Database scheme is up to date")
        } else {
            database.logger.log(Level.INFO, "Applied ${result.size} migrations: ${result.joinToString(", ") { it.path }}")
        }

        return result
    }

    private fun applyMigration(database: SqiffyDatabase, ledger: ChangelogLedger, migration: Migration) {
        val jdbi = database.getJdbi()

        try {
            jdbi.useTransaction<Exception> { handle ->
                executeBody(handle, migration.body)
                ledger.record(handle, migration.path, migration.checksum)
            }
        } catch (exception: Exception) {
            if (!isTransactionBlockError(exception)) {
                throw MigrationException("Failed to apply migration: ${migration.path}", exception)
            }

            database.logger.log(Level.WARN, "Migration ${migration.path} cannot run inside a transaction block, retrying with autocommit")

            jdbi.useHandle<Exception> { handle ->
                val connection = handle.connection
                val previousAutoCommit = connection.autoCommit
                connection.autoCommit = true
                try {
                    executeBody(handle, migration.body)
                    ledger.record(handle, migration.path, migration.checksum)
                } finally {
                    connection.autoCommit = previousAutoCommit
                }
            }
        }
    }

    private fun executeBody(handle: Handle, body: String) {
        handle.connection.createStatement().use { it.execute(body) }
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
        ledger: ChangelogLedger,
        migrations: List<Migration>,
        config: LiquibaseImport
    ): List<AppliedChangeset> {
        val jdbi = database.getJdbi()

        val liquibaseFilenames = jdbi.withHandle<List<String>?, Exception> { handle ->
            if (!tableExists(handle, config.tableName)) {
                null
            } else {
                handle.createQuery("SELECT filename FROM ${config.tableName}")
                    .mapTo(String::class.java)
                    .list()
            }
        } ?: return emptyList()

        if (liquibaseFilenames.isEmpty()) {
            return emptyList()
        }

        database.logger.log(
            Level.INFO,
            "Detected Liquibase changelog table '${config.tableName}' with ${liquibaseFilenames.size} applied changesets, importing state"
        )

        val imported = mutableListOf<AppliedChangeset>()

        jdbi.useTransaction<Exception> { handle ->
            migrations.forEach { migration ->
                if (liquibaseFilenames.any { liquibaseMatches(migration.path, it) }) {
                    ledger.record(handle, migration.path, migration.checksum)
                    imported.add(AppliedChangeset(migration.path, migration.checksum, ChangesetSource.IMPORTED))
                }
            }
        }

        database.logger.log(Level.INFO, "Imported ${imported.size} changesets from Liquibase state")
        return imported
    }

    private fun tableExists(handle: Handle, name: String): Boolean {
        val metaData = handle.connection.metaData

        for (candidate in linkedSetOf(name, name.lowercase(), name.uppercase())) {
            metaData.getTables(null, null, candidate, arrayOf("TABLE")).use { resultSet ->
                if (resultSet.next()) {
                    return true
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
