package com.dzikoysk.sqiffy.changelog

import com.dzikoysk.sqiffy.SqiffyDatabase
import com.dzikoysk.sqiffy.definition.DataType.TEXT
import com.dzikoysk.sqiffy.definition.PropertyData
import com.dzikoysk.sqiffy.dsl.ParameterAllocator
import org.slf4j.event.Level

class Migrator(private val database: SqiffyDatabase) {

    data class SqiffyMetadataTable(val name: String = "sqiffy_metadata")

    @Suppress("RemoveRedundantQualifierName")
    fun runMigrations(metadataTable: SqiffyMetadataTable = SqiffyMetadataTable(), changeLog: ChangeLog) {
        val tableName = metadataTable.name

        val propertyColumn = PropertyData(
            name = "property",
            type = TEXT
        )

        database.getJdbi().useHandle<Exception> { handle ->
            handle.execute(
                database.sqlSchemeGenerator.createTable(
                    name = tableName,
                    properties = listOf(propertyColumn),
                    enums = Enums()
                ).also { database.logger.log(Level.INFO, it) }
            )
        }

        database.getJdbi().useTransaction<Exception> { transaction ->
            val currentVersion = transaction
                .select(
                    database.sqlQueryGenerator.createSelectQuery(
                        tableName = tableName,
                        selected = listOf(propertyColumn).map { it.name },
                        where = """"${propertyColumn.name}" = :version"""
                    ).first,
                )
                .bind("version", "version")
                .mapTo(String::class.java)
                .firstOrNull()

            database.logger.log(Level.INFO, "Current version of database scheme: $currentVersion")

            val allChanges = changeLog.getAllChanges()

            val changesToApply = currentVersion
                ?.let { allChanges }
                ?.dropWhile { (version, _) -> version != currentVersion } // drop old versions
                ?.drop(1) // drop current version
                ?: allChanges

            if (changesToApply.isEmpty()) {
                database.logger.log(Level.INFO, "Database scheme is up to date")
                return@useTransaction
            }

            database.logger.log(Level.INFO, "Changes to apply: ${changesToApply.joinToString(", ")}")

            val latestVersion = changesToApply
                .last()
                .version

            database.logger.log(Level.INFO, "Latest version of database scheme: $latestVersion")

            if (currentVersion == latestVersion) {
                database.logger.log(Level.INFO, "Database scheme is up to date")
                return@useTransaction
            }

            changesToApply.forEach { (version, changes) ->
                database.logger.log(Level.INFO, "Applying changes for version $version")

                changes.forEach {
                    database.logger.log(Level.DEBUG, it.query)
                    transaction.execute(it.query)
                }
            }

            when (currentVersion) {
                null ->
                    transaction
                        .createUpdate(
                            database.sqlQueryGenerator.createInsertQuery(
                                allocator = ParameterAllocator(),
                                tableName = tableName,
                                columns = listOf(propertyColumn).map { it.name },
                            ).first
                        )
                        .bind("0", latestVersion)
                        .execute()
                else ->
                    transaction
                        .createUpdate(
                            database.sqlQueryGenerator.createUpdateQuery(
                                tableName = tableName,
                                columns = listOf(propertyColumn).map { it.name },
                            ).first
                        )
                        .bind(propertyColumn.name, latestVersion)
                        .execute()
            }
        }
    }

}