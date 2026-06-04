package com.dzikoysk.sqiffy.e2e

import com.dzikoysk.sqiffy.e2e.specification.SqiffyE2ETestSpecification
import com.dzikoysk.sqiffy.e2e.specification.postgresDataSource
import com.dzikoysk.sqiffy.migrator.ChecksumPolicy
import com.dzikoysk.sqiffy.migrator.FileMigrator
import com.dzikoysk.sqiffy.migrator.MigrationException
import com.dzikoysk.sqiffy.migrator.classpathResource
import com.dzikoysk.sqiffy.shared.createSQLiteDataSource
import com.zaxxer.hikari.HikariDataSource
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertDoesNotThrow

internal class EmbeddedPostgresFileMigratorE2ETest : FileMigratorE2ETest() {
    private val postgres = postgresDataSource()
    override fun createDataSource(): HikariDataSource = postgres.dataSource
    @AfterEach
    fun stop() { postgres.embeddedPostgres.close() }
}

internal abstract class FileMigratorE2ETest : SqiffyE2ETestSpecification(runMigrations = false) {

    private val index = "filemigrator/changelog.index"

    private val migrationPaths = listOf(
        "filemigrator/1.0.0/001-create-users.sql",
        "filemigrator/1.0.0/002-add-email.sql",
        "filemigrator/1.0.0/003-add-counter-function.sql",
    )

    @Test
    fun `should apply all migrations against an empty database and be idempotent`() {
        // when: migrations are run against an empty database
        val applied = assertDoesNotThrow { database.runMigrations(FileMigrator(index)) }

        // then: every script is applied, in order
        assertThat(applied).isEqualTo(migrationPaths)

        // and: the schema produced by the scripts actually exists and works
        assertThat(tableExists("users")).isTrue()
        assertThat(tableExists("counters")).isTrue()
        database.getJdbi().useHandle<Exception> { it.execute("INSERT INTO users(name, email) VALUES ('alice', 'alice@example.com')") }
        // the dollar-quoted PL/pgSQL function (which a naive ';' splitter would have broken) is callable
        val firstBump = database.getJdbi().withHandle<Int, Exception> { it.createQuery("SELECT bump_counter('visits')").mapTo(Int::class.java).one() }
        assertThat(firstBump).isEqualTo(1)

        // when: migrations are run again
        val reRun = assertDoesNotThrow { database.runMigrations(FileMigrator(index)) }
        // then: nothing is re-applied
        assertThat(reRun).isEmpty()
    }

    @Test
    fun `should run a path listed twice in the index only once`() {
        val duplicatingIndex = "duplicating.index"
        val loader: (String) -> String? = { requested ->
            if (requested == duplicatingIndex) "${migrationPaths[0]}\n${migrationPaths[0]}" else classpathResource(requested)
        }

        // when: the same script path appears twice in the index
        val applied = assertDoesNotThrow { database.runMigrations(FileMigrator(duplicatingIndex, resourceLoader = loader)) }

        // then: it is applied exactly once (a second CREATE TABLE would otherwise fail)
        assertThat(applied).containsExactly(migrationPaths[0])
        assertThat(tableExists("users")).isTrue()
    }

    @Test
    fun `should fail on checksum drift of an already-applied script by default`() {
        // given: migrations have been applied
        database.runMigrations(FileMigrator(index))

        // when: an already-applied script's content changes and the migrator runs again with FAIL (default)
        val driftedLoader = loaderWithOverride(migrationPaths[1], "ALTER TABLE users ADD COLUMN nickname varchar(128);")

        // then: the migration aborts
        assertThatThrownBy { database.runMigrations(FileMigrator(index, resourceLoader = driftedLoader)) }
            .isInstanceOf(MigrationException::class.java)
            .hasMessageContaining("Checksum mismatch")
    }

    @Test
    fun `should tolerate checksum drift when policy is WARN`() {
        // given: migrations have been applied
        database.runMigrations(FileMigrator(index))

        // when: an already-applied script's content changes and the migrator runs with WARN
        val driftedLoader = loaderWithOverride(migrationPaths[1], "ALTER TABLE users ADD COLUMN nickname varchar(128);")
        val reRun = assertDoesNotThrow {
            database.runMigrations(FileMigrator(index, checksumPolicy = ChecksumPolicy.WARN, resourceLoader = driftedLoader))
        }

        // then: the drift is treated as already-applied and nothing is executed
        assertThat(reRun).isEmpty()
    }

    @Test
    fun `should silently continue on checksum drift when policy is IGNORE`() {
        // given: migrations have been applied
        database.runMigrations(FileMigrator(index))

        // when: an already-applied script's content changes and the migrator runs with IGNORE
        val driftedLoader = loaderWithOverride(migrationPaths[1], "ALTER TABLE users ADD COLUMN nickname varchar(128);")
        val reRun = assertDoesNotThrow {
            database.runMigrations(FileMigrator(index, checksumPolicy = ChecksumPolicy.IGNORE, resourceLoader = driftedLoader))
        }

        // then: nothing is executed and no warning path is taken
        assertThat(reRun).isEmpty()
    }

    @Test
    fun `should ignore existing Liquibase state by default`() {
        // given: a Liquibase changelog table exists, but no real schema (tables) yet
        database.getJdbi().useHandle<Exception> { handle ->
            handle.execute("CREATE TABLE databasechangelog (id varchar(255), author varchar(255), filename varchar(255))")
            migrationPaths.forEach { filename ->
                handle.execute("INSERT INTO databasechangelog(id, author, filename) VALUES (?, ?, ?)", "1", "test", filename)
            }
        }

        // when: the migrator runs without opting into the Liquibase import
        val applied = assertDoesNotThrow { database.runMigrations(FileMigrator(index)) }

        // then: the Liquibase table is ignored and every script is executed fresh
        assertThat(applied).isEqualTo(migrationPaths)
        assertThat(tableExists("users")).isTrue()
        assertThat(tableExists("counters")).isTrue()
    }

    @Test
    fun `should import existing Liquibase state instead of re-running scripts`() {
        // given: a database previously managed by Liquibase (changesets recorded, by filename)
        database.getJdbi().useHandle<Exception> { handle ->
            handle.execute("CREATE TABLE databasechangelog (id varchar(255), author varchar(255), filename varchar(255))")
            migrationPaths.forEach { filename ->
                handle.execute("INSERT INTO databasechangelog(id, author, filename) VALUES (?, ?, ?)", "1", "test", filename)
            }
        }

        // when: the file migrator runs for the first time, opting into the Liquibase import
        val migrator = { FileMigrator(index, liquibaseChangelogTable = "databasechangelog") }
        val applied = assertDoesNotThrow { database.runMigrations(migrator()) }

        // then: nothing is executed — the existing state is adopted
        assertThat(applied).isEmpty()
        // and: the scripts were genuinely skipped (the table 001 would have created does not exist)
        assertThat(tableExists("users")).isFalse()

        // and: the imported state is persisted, so a subsequent run is also a no-op
        assertThat(database.runMigrations(migrator())).isEmpty()
    }

    @Test
    fun `should apply only migrations not already recorded by Liquibase`() {
        // given: Liquibase recorded only the first two changesets, and their tables already exist
        database.getJdbi().useHandle<Exception> { handle ->
            handle.execute("CREATE TABLE users (id serial NOT NULL, name varchar(64) NOT NULL, email varchar(128), CONSTRAINT pk_users PRIMARY KEY (id))")
            handle.execute("CREATE TABLE databasechangelog (id varchar(255), author varchar(255), filename varchar(255))")
            handle.execute("INSERT INTO databasechangelog(id, author, filename) VALUES ('1', 'test', ?)", migrationPaths[0])
            handle.execute("INSERT INTO databasechangelog(id, author, filename) VALUES ('2', 'test', ?)", migrationPaths[1])
        }

        // when: the file migrator runs, opting into the Liquibase import
        val applied = assertDoesNotThrow { database.runMigrations(FileMigrator(index, liquibaseChangelogTable = "databasechangelog")) }

        // then: only the third script (unknown to Liquibase) is executed
        assertThat(applied).containsExactly(migrationPaths[2])
        assertThat(tableExists("counters")).isTrue()
    }

    @Test
    fun `should run a migration marked no-transaction outside a transaction`() {
        val notxIndex = "filemigrator-notx/changelog.index"

        // when: a CREATE INDEX CONCURRENTLY script carrying the no-transaction marker is applied
        val applied = assertDoesNotThrow { database.runMigrations(FileMigrator(notxIndex)) }

        // then: it runs (CONCURRENTLY cannot run inside a transaction) and the index exists
        assertThat(applied).containsExactly(
            "filemigrator-notx/001-create-widgets.sql",
            "filemigrator-notx/002-add-index-concurrently.sql",
        )
        assertThat(indexExists("widgets_name_idx")).isTrue()
    }

    @Test
    fun `should fail a CONCURRENTLY script when the no-transaction marker is missing`() {
        val notxIndex = "filemigrator-notx/changelog.index"
        // same script without the marker, so it runs inside a transaction
        val withoutMarker = loaderWithOverride(
            "filemigrator-notx/002-add-index-concurrently.sql",
            "CREATE INDEX CONCURRENTLY widgets_name_idx ON widgets (name);"
        )

        // then: PostgreSQL refuses CONCURRENTLY inside a transaction and the migration aborts
        assertThatThrownBy { database.runMigrations(FileMigrator(notxIndex, resourceLoader = withoutMarker)) }
            .isInstanceOf(MigrationException::class.java)
    }

    private fun tableExists(name: String): Boolean =
        database.getJdbi().withHandle<Boolean, Exception> { handle ->
            handle.connection.metaData.getTables(null, null, name, arrayOf("TABLE")).use { it.next() }
        }

    private fun indexExists(name: String): Boolean =
        database.getJdbi().withHandle<Boolean, Exception> { handle ->
            handle.createQuery("SELECT count(*) FROM pg_indexes WHERE indexname = :name")
                .bind("name", name)
                .mapTo(Int::class.java)
                .one() > 0
        }

    private fun loaderWithOverride(path: String, content: String): (String) -> String? =
        { requested -> if (requested == path) content else classpathResource(requested) }

}

internal class SqliteFileMigratorE2ETest : SqiffyE2ETestSpecification(runMigrations = false) {

    override fun createDataSource(): HikariDataSource = createSQLiteDataSource()

    @Test
    fun `should apply every statement of a multi-statement script on sqlite`() {
        val index = "filemigrator-sqlite/changelog.index"

        // when: a two-statement script is applied
        val applied = assertDoesNotThrow { database.runMigrations(FileMigrator(index)) }

        // then: both statements ran — not just the first, which is what SQLite's driver does for a
        // multi-statement execute()
        assertThat(applied).containsExactly("filemigrator-sqlite/001-multi-statement.sql")
        assertThat(tableExists("alpha")).isTrue()
        assertThat(tableExists("beta")).isTrue()

        // and: re-running is a no-op
        assertThat(database.runMigrations(FileMigrator(index))).isEmpty()
    }

    private fun tableExists(name: String): Boolean =
        database.getJdbi().withHandle<Boolean, Exception> { handle ->
            handle.connection.metaData.getTables(null, null, name, arrayOf("TABLE")).use { it.next() }
        }

}
