package com.dzikoysk.sqiffy

import com.dzikoysk.sqiffy.DataType.TEXT
import com.dzikoysk.sqiffy.changelog.ChangeLog
import com.dzikoysk.sqiffy.changelog.ChangeLogGenerator
import com.dzikoysk.sqiffy.sql.MySqlGenerator
import com.dzikoysk.sqiffy.sql.PostgreSqlGenerator
import com.dzikoysk.sqiffy.sql.SqlGenerator
import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import org.jdbi.v3.core.Jdbi
import org.jdbi.v3.core.kotlin.KotlinPlugin
import org.jdbi.v3.sqlobject.kotlin.KotlinSqlObjectPlugin
import org.slf4j.event.Level
import java.io.Closeable
import kotlin.reflect.KClass

enum class Dialect  {
    MYSQL,
    POSTGRESQL
}

open class Sqiffy(
    private val logger: SqiffyLogger = StdoutSqiffyLogger(),
    val dataSource: HikariDataSource
) : Closeable {

    private val localJdbi = Jdbi.create(dataSource)
        .installPlugin(KotlinPlugin())
        .installPlugin(KotlinSqlObjectPlugin())

    private val dialect = when {
        dataSource.jdbcUrl.contains("mysql", ignoreCase = true) -> Dialect.MYSQL
        dataSource.jdbcUrl.contains("postgresql", ignoreCase = true) -> Dialect.POSTGRESQL
        else -> throw IllegalArgumentException("Unsupported dialect for ${dataSource.jdbcUrl}")
    }

    val sqlGenerator: SqlGenerator = when (dialect) {
        Dialect.MYSQL -> MySqlGenerator
        Dialect.POSTGRESQL -> PostgreSqlGenerator
    }

    private val changeLogGenerator = ChangeLogGenerator(
        sqlGenerator = sqlGenerator,
        typeFactory = RuntimeTypeFactory()
    )

    fun generateChangeLog(vararg classes: KClass<*>): ChangeLog =
        changeLogGenerator.generateChangeLog(*classes)

    data class SqiffyMetadataTable(val name: String = "sqiffy_metadata")

    @Suppress("RemoveRedundantQualifierName")
    fun runMigrations(metadataTable: SqiffyMetadataTable = SqiffyMetadataTable(), changeLog: ChangeLog) {
        val tableName = metadataTable.name

        val propertyColumn = PropertyData(
            name = "property",
            type = TEXT
        )

        localJdbi.useHandle<Exception> { handle ->
            handle.execute(
                sqlGenerator.createTable(
                    name = tableName,
                    properties = listOf(propertyColumn)
                ).also { logger.log(Level.INFO, it) }
            )
        }

        localJdbi.useTransaction<Exception> { transaction ->
            val currentVersion = transaction
                .select(
                    sqlGenerator.createSelectQuery(
                        tableName = tableName,
                        columns = listOf(propertyColumn).map { it.name },
                        where = """"${propertyColumn.name}" = :version"""
                    ),
                )
                .bind("version", "version")
                .mapTo(String::class.java)
                .firstOrNull()

            logger.log(Level.INFO, "Current version of database scheme: $currentVersion")

            val changesToApply = currentVersion
                ?.let { changeLog.changes }
                ?.dropWhile { (version, _) -> version != currentVersion } // drop old versions
                ?.drop(1) // drop current version
                ?: changeLog.changes.toList()

            if (changesToApply.isEmpty()) {
                logger.log(Level.INFO, "Database scheme is up to date")
                return@useTransaction
            }

            logger.log(Level.INFO, "Changes to apply: ${changesToApply.joinToString(", ")}")

            val latestVersion = changesToApply
                .last()
                .version

            logger.log(Level.INFO, "Latest version of database scheme: $latestVersion")

            if (currentVersion == latestVersion) {
                logger.log(Level.INFO, "Database scheme is up to date")
                return@useTransaction
            }

            changesToApply.forEach { (version, changes) ->
                logger.log(Level.INFO, "Applying changes for version $version")

                changes.forEach {
                    logger.log(Level.DEBUG, it.query)
                    transaction.execute(it.query)
                }
            }

            when (currentVersion) {
                null ->
                    transaction
                        .createUpdate(
                            sqlGenerator.createInsertQuery(
                                tableName = tableName,
                                columns = listOf(propertyColumn).map { it.name },
                            )
                        )
                        .bind(propertyColumn.name, latestVersion)
                        .execute()
                else ->
                    transaction
                        .createUpdate(
                            sqlGenerator.createUpdateQuery(
                                tableName = tableName,
                                columns = listOf(propertyColumn).map { it.name },
                            )
                        ).bind(propertyColumn.name, latestVersion)
                        .execute()
            }
        }
        /*
        transaction {
            SchemaUtils.create(SqiffyChangeLogTable)

            val currentVersion = SqiffyChangeLogTable.selectAll()
                .map { it[SqiffyChangeLogTable.version] }
                .firstOrNull()

            logger.log(Level.INFO, "Current version of database scheme: $currentVersion")

            val changesToApply = currentVersion
                ?.let { changeLog.changes }
                ?.dropWhile { (version, _) -> version != currentVersion } // drop old versions
                ?.drop(1) // drop current version
                ?: changeLog.changes.toList()

            if (changesToApply.isEmpty()) {
                logger.log(Level.INFO, "Database scheme is up to date")
                return@transaction
            }

            logger.log(Level.INFO, "Changes to apply: ${changesToApply.joinToString(", ")}")

            val latestVersion = changesToApply
                .last()
                .version

            logger.log(Level.INFO, "Latest version of database scheme: $latestVersion")

            if (currentVersion == latestVersion) {
                logger.log(Level.INFO, "Database scheme is up to date")
                return@transaction
            }

            changesToApply.forEach { (version, changes) ->
                logger.log(Level.INFO, "Applying changes for version $version")

                changes.forEach {
                    logger.log(Level.DEBUG, it.query)
                    TransactionManager.current().connection.executeQuery("${it.query};")
                }
            }

            when (currentVersion) {
                null -> SqiffyChangeLogTable.insert { it[SqiffyChangeLogTable.version] = latestVersion }
                else -> SqiffyChangeLogTable.update({ SqiffyChangeLogTable.version eq currentVersion }) { it[SqiffyChangeLogTable.version] = latestVersion }
            }
        }

         */
    }

    fun getJdbi(): Jdbi =
        localJdbi

    override fun close() {
        dataSource.close()
    }

}

fun createHikariDataSource(
    driver: String,
    url: String,
    threadPool: Int = 1,
    username: String? = null,
    password: String? = null
): HikariDataSource =
    HikariDataSource(
        HikariConfig().apply {
            this.jdbcUrl = url
            this.driverClassName = driver
            this.maximumPoolSize = threadPool
            username?.also { this.username = it }
            password?.also { this.password = it }
        }
    )
