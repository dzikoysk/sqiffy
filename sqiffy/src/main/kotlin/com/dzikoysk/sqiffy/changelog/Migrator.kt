package com.dzikoysk.sqiffy.changelog

import com.dzikoysk.sqiffy.SqiffyDatabase
import com.dzikoysk.sqiffy.definition.DataType.TEXT
import com.dzikoysk.sqiffy.definition.PropertyData
import com.dzikoysk.sqiffy.dsl.Column
import com.dzikoysk.sqiffy.dsl.Table
import com.dzikoysk.sqiffy.dsl.Values
import com.dzikoysk.sqiffy.dsl.generator.ParameterAllocator
import com.dzikoysk.sqiffy.dsl.generator.bindArguments
import com.dzikoysk.sqiffy.dsl.generator.toQueryColumn
import org.slf4j.event.Level

class Migrator(private val database: SqiffyDatabase) {

    class SqiffyMetadataTable(name: String = "sqiffy_metadata") : Table(name) {
        val property: Column<String> = text("property", "text")
    }

    @Suppress("RemoveRedundantQualifierName")
    fun runMigrations(metadataTable: SqiffyMetadataTable = SqiffyMetadataTable(), changeLog: ChangeLog) {
        val tableName = metadataTable.getTableName()

        val columnProperty = PropertyData(
            name = "property",
            type = TEXT
        )

        val queryColumn = metadataTable.property.toQueryColumn()

        database.getJdbi().useHandle<Exception> { handle ->
            handle.execute(
                database.sqlSchemeGenerator.createTable(
                    name = tableName,
                    properties = listOf(columnProperty),
                    enums = Enums()
                ).also { database.logger.log(Level.INFO, it) }
            )
        }

        database.getJdbi().useTransaction<Exception> { transaction ->
            val currentVersion = transaction
                .select(
                    database.sqlQueryGenerator.createSelectQuery(
                        tableName = tableName,
                        selected = listOf(queryColumn),
                        where = """"${columnProperty.name}" = :version"""
                    ).query,
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

            val allocator = ParameterAllocator()

            val (query, arguments) = when (currentVersion) {
                null -> database.sqlQueryGenerator.createInsertQuery(
                    allocator = allocator,
                    tableName = tableName,
                    columns = listOf(queryColumn)
                )
                else -> database.sqlQueryGenerator.createUpdateQuery(
                    allocator = allocator,
                    tableName = tableName,
                    columns = listOf(queryColumn),
                )
            }

            val values = Values()
            values[metadataTable.property] = latestVersion

            transaction
                .createUpdate(query)
                .bindArguments(arguments, values)
                .execute()
        }
    }

}