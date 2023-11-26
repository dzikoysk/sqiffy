package com.dzikoysk.sqiffy.e2e.specification

import com.dzikoysk.sqiffy.Slf4JSqiffyLogger
import com.dzikoysk.sqiffy.Sqiffy
import com.dzikoysk.sqiffy.SqiffyDatabase
import com.dzikoysk.sqiffy.definition.ChangelogProvider
import com.dzikoysk.sqiffy.e2e.GuildDefinition
import com.dzikoysk.sqiffy.e2e.UserDefinition
import com.dzikoysk.sqiffy.migrator.LiquibaseMigrator
import com.dzikoysk.sqiffy.migrator.SqiffyMigrator
import com.dzikoysk.sqiffy.shared.createHikariDataSource
import com.zaxxer.hikari.HikariDataSource
import io.zonky.test.db.postgres.embedded.EmbeddedPostgres
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.slf4j.LoggerFactory

abstract class SqiffyE2ETestSpecification(
    private val runMigrations: Boolean = true,
    private val migrationProvider: ChangelogProvider = ChangelogProvider.SQIFFY
) {

    lateinit var database: SqiffyDatabase

    abstract fun createDataSource(): HikariDataSource

    @BeforeEach
    fun setup() {
        this.database = Sqiffy.createDatabase(
            dataSource = createDataSource(),
            logger = Slf4JSqiffyLogger(LoggerFactory.getLogger(SqiffyDatabase::class.java))
        )

        if (runMigrations) {
            when (migrationProvider) {
                ChangelogProvider.SQIFFY -> database.runMigrations(SqiffyMigrator(
                    changeLog = database.generateChangeLog(UserDefinition::class, GuildDefinition::class)
                ))
                ChangelogProvider.LIQUIBASE -> database.runMigrations(LiquibaseMigrator())
            }
        }
    }

    @AfterEach
    fun tearDown() {
        database.close()
    }

}

data class Postgres(
    val embeddedPostgres: EmbeddedPostgres,
    val dataSource: HikariDataSource
)

fun postgresDataSource(): Postgres {
    val postgres = EmbeddedPostgres.start()

    val dataSource = createHikariDataSource(
        driver = "org.postgresql.Driver",
        url = "jdbc:postgresql://localhost:${postgres!!.port}/postgres",
        username = "postgres",
        password = "postgres"
    )

    return Postgres(postgres, dataSource)
}
