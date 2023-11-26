@file:Suppress("DEPRECATION")

package com.dzikoysk.sqiffy.migrator

import org.slf4j.event.Level as Slf4jLevel
import com.dzikoysk.sqiffy.SqiffyDatabase
import java.util.logging.Level
import liquibase.Liquibase
import liquibase.Scope
import liquibase.Scope.Attr
import liquibase.UpdateSummaryEnum
import liquibase.command.CommandScope
import liquibase.command.core.UpdateCommandStep
import liquibase.command.core.helpers.DbUrlConnectionCommandStep
import liquibase.command.core.helpers.ShowSummaryArgument
import liquibase.database.DatabaseFactory
import liquibase.database.jvm.JdbcConnection
import liquibase.logging.LogMessageFilter
import liquibase.logging.LogService
import liquibase.logging.Logger
import liquibase.logging.core.AbstractLogger
import liquibase.plugin.Plugin
import liquibase.resource.ClassLoaderResourceAccessor
import liquibase.ui.ConsoleUIService

class LiquibaseMigrator(
    private val changelogFile: String = "/liquibase/changelog-master.xml"
) : Migrator<Unit> {

    override fun runMigrations(database: SqiffyDatabase) {
        val config = mutableMapOf<String, Any>()

        config[Attr.logService.name] = object : LogService {
            override fun getLog(clazz: Class<*>): Logger {
                val logger = database.logger.createLogger(clazz)

                return object : AbstractLogger() {
                    override fun log(level: Level, message: String?, e: Throwable?) {
                        logger.log(
                            when (level) {
                                Level.SEVERE -> Slf4jLevel.ERROR
                                Level.WARNING -> Slf4jLevel.WARN
                                Level.INFO -> Slf4jLevel.INFO
                                Level.CONFIG -> Slf4jLevel.INFO
                                Level.FINE -> Slf4jLevel.DEBUG
                                Level.FINER -> Slf4jLevel.TRACE
                                Level.FINEST -> Slf4jLevel.TRACE
                                else -> throw IllegalArgumentException("Unknown level: $level")
                            },
                            message
                                ?: ""
                        )
                    }
                }
            }
            private var filter: LogMessageFilter? = null
            override fun getFilter(): LogMessageFilter? = filter
            override fun setFilter(filter: LogMessageFilter?) { this.filter = filter }
            override fun getPriority(): Int = Plugin.PRIORITY_DEFAULT
            override fun close() {}
        }

        val liquibaseUiLogger = database.logger.createLogger(LiquibaseMigrator::class.java)

        config[Attr.ui.name] = object : ConsoleUIService() {
            override fun sendMessage(message: String) {
                liquibaseUiLogger.log(Slf4jLevel.INFO, message)
            }
        }

        Scope.child(config) {
            val connection = JdbcConnection(database.dataSource.connection)
            val liquibaseDatabase = DatabaseFactory.getInstance().findCorrectDatabaseImplementation(connection)

            val liquibase = Liquibase(
                changelogFile,
                ClassLoaderResourceAccessor(),
                liquibaseDatabase
            )

            val updateCommand = CommandScope(*UpdateCommandStep.COMMAND_NAME)
            updateCommand.addArgumentValue(DbUrlConnectionCommandStep.DATABASE_ARG, liquibase.database)
            updateCommand.addArgumentValue(UpdateCommandStep.CHANGELOG_FILE_ARG, changelogFile)
            // disable summary
            updateCommand.addArgumentValue(ShowSummaryArgument.SHOW_SUMMARY, UpdateSummaryEnum.OFF)
            updateCommand.execute()

            liquibase.close()
        }
    }

}