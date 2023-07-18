package com.dzikoysk.sqiffy.changelog

import com.dzikoysk.sqiffy.SqiffyDatabase
import com.dzikoysk.sqiffy.dsl.Column
import com.dzikoysk.sqiffy.dsl.Table
import com.dzikoysk.sqiffy.dsl.Values
import com.dzikoysk.sqiffy.dsl.eq
import com.dzikoysk.sqiffy.dsl.generator.ParameterAllocator
import com.dzikoysk.sqiffy.dsl.generator.SqlQueryGenerator.GeneratorResult
import com.dzikoysk.sqiffy.dsl.generator.bindArguments
import com.dzikoysk.sqiffy.dsl.generator.toQueryColumn
import org.slf4j.event.Level

private const val VERSION_KEY = "version"

class SqiffyMetadataTable(name: String = "sqiffy_metadata") : Table(name) {
    val key: Column<String> = text("key", "varchar(32)")
    val value: Column<String> = text("value", "text")
}

internal data class VersionCallback(
    val before: (() -> Unit)? = null,
    val after: (() -> Unit)? = null
)

class VersionCallbacks {
    internal val versionCallbacks = mutableMapOf<Version, VersionCallback>()
    fun before(version: Version, callback: () -> Unit): VersionCallbacks = also { versionCallbacks.merge(version, VersionCallback(before = callback)) { old, new -> old.copy(before = new.before) } }
    fun after(version: Version, callback: () -> Unit): VersionCallbacks = also { versionCallbacks.merge(version, VersionCallback(after = callback)) { old, new -> old.copy(after = new.after) } }
}

class Migrator(
    private val database: SqiffyDatabase,
    private val metadataTable: SqiffyMetadataTable,
    private val changeLog: ChangeLog,
    private val versionCallbacks: VersionCallbacks,
) {

    fun runMigrations(): List<Version> {
        val tableName = metadataTable.getName()

        database.getJdbi().useHandle<Exception> { handle ->
            handle.execute(
                database.sqlSchemeGenerator.createTable(
                    name = tableName,
                    properties = metadataTable.getColumns().map { it.getPropertyData() },
                    enums = Enums()
                ).also { database.logger.log(Level.INFO, it) }
            )
        }

        return database.getJdbi().inTransaction<List<Version>, Exception> { transaction ->
            val currentVersion = let {
                val (whereQuery, whereArguments) = database.sqlQueryGenerator.createExpression(
                    allocator = ParameterAllocator(),
                    expression = metadataTable.key eq VERSION_KEY
                )

                transaction
                    .select(
                        database.sqlQueryGenerator.createSelectQuery(
                            tableName = tableName,
                            selected = listOf(metadataTable.value),
                            where = whereQuery,
                        ).query,
                    )
                    .bindArguments(whereArguments)
                    .mapTo(String::class.java)
                    .firstOrNull()
            }

            database.logger.log(Level.INFO, "Current version of database scheme: $currentVersion")

            val allChanges = changeLog.getAllChanges()

            val changesToApply = currentVersion
                ?.let { allChanges }
                ?.dropWhile { (version, _) -> version != currentVersion } // drop old versions
                ?.drop(1) // drop current version
                ?: allChanges

            if (changesToApply.isEmpty()) {
                database.logger.log(Level.INFO, "Database scheme is up to date")
                return@inTransaction emptyList()
            }

            database.logger.log(Level.INFO, "Changes to apply: ${changesToApply.joinToString(", ")}")

            val latestVersion = changesToApply
                .last()
                .version

            database.logger.log(Level.INFO, "Latest version of database scheme: $latestVersion")

            if (currentVersion == latestVersion) {
                database.logger.log(Level.INFO, "Database scheme is up to date")
                return@inTransaction emptyList()
            }

            changesToApply.forEach { (version, changes) ->
                database.logger.log(Level.INFO, "Applying changes for version $version")

                val callback = versionCallbacks.versionCallbacks[version]
                callback?.before?.invoke()

                changes.forEach {
                    database.logger.log(Level.DEBUG, it.query)
                    transaction.execute(it.query)
                }

                callback?.after?.invoke()
            }

            val allocator = ParameterAllocator()
            val values = Values()

            val (query, arguments) = when (currentVersion) {
                null -> database.sqlQueryGenerator.createInsertQuery(
                    allocator = allocator,
                    tableName = tableName,
                    columns = listOf(
                        metadataTable.key.toQueryColumn(),
                        metadataTable.value.toQueryColumn(),
                    ),
                    autogeneratedKey = null
                ).updateResult
                else -> {
                    val (whereQuery, whereArguments) = database.sqlQueryGenerator.createExpression(
                        allocator = allocator,
                        expression = metadataTable.key eq VERSION_KEY
                    )

                    val (updateQuery, updateArguments) = database.sqlQueryGenerator.createUpdateQuery(
                        allocator = allocator,
                        tableName = tableName,
                        argumentColumns = listOf(
                            metadataTable.value.toQueryColumn(),
                        ),
                        where = whereQuery
                    )

                    GeneratorResult(
                        query = updateQuery,
                        arguments = (whereArguments + updateArguments)
                    )
                }
            }

            values[metadataTable.key] = VERSION_KEY
            values[metadataTable.value] = latestVersion

            transaction
                .createUpdate(query)
                .bindArguments(arguments, values)
                .execute()

            changesToApply.map { it.version }
        }
    }

}