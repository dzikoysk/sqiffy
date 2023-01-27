package com.dzikoysk.sqiffy

import com.dzikoysk.sqiffy.changelog.ChangeLog
import com.dzikoysk.sqiffy.changelog.ChangeLogGenerator
import com.dzikoysk.sqiffy.shared.executeQuery
import com.zaxxer.hikari.HikariDataSource
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.Transaction
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.TransactionManager
import org.jetbrains.exposed.sql.update
import org.slf4j.event.Level
import java.io.Closeable
import kotlin.reflect.KClass
import org.jetbrains.exposed.sql.transactions.transaction as runTransaction

open class Sqiffy(
    val databaseConnection: DatabaseConnection,
    private val changeLogGenerator: ChangeLogGenerator = ChangeLogGenerator(RuntimeTypeFactory()),
    private val logger: SqiffyLogger = StdoutSqiffyLogger()
) : Closeable {

    constructor(
        dataSource: HikariDataSource,
        logger: SqiffyLogger = StdoutSqiffyLogger()
    ) : this(
        databaseConnection = dataSource.toDatabaseConnection(),
        logger = logger
    )

    private object SqiffyChangeLogTable : Table("sqiffy_scheme_version") {
        val version = varchar("version", 128)
    }

    fun <R> transaction(statement: Transaction.() -> R) =
        runTransaction(databaseConnection.database, statement)

    fun generateChangeLog(vararg classes: KClass<*>): ChangeLog =
        changeLogGenerator.generateChangeLog(*classes)

    fun generateChangeLogForAll(): ChangeLog =
        changeLogGenerator.generateChangeLog()

    @Suppress("RemoveRedundantQualifierName")
    fun runMigrations(changeLog: ChangeLog) {
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

                changes.forEach { change ->
                    logger.log(Level.DEBUG, change)
                    TransactionManager.current().connection.executeQuery("$change;")
                }
            }

            when (currentVersion) {
                null -> SqiffyChangeLogTable.insert { it[SqiffyChangeLogTable.version] = latestVersion }
                else -> SqiffyChangeLogTable.update({ SqiffyChangeLogTable.version eq currentVersion }) { it[SqiffyChangeLogTable.version] = latestVersion }
            }
        }
    }

    override fun close() {
        databaseConnection.close()
    }

}