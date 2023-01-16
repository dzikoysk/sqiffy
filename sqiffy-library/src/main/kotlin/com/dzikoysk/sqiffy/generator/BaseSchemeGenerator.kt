package com.dzikoysk.sqiffy.generator

import com.dzikoysk.sqiffy.ChangeLog
import com.dzikoysk.sqiffy.DataType.BINARY
import com.dzikoysk.sqiffy.DataType.BLOB
import com.dzikoysk.sqiffy.DataType.BOOLEAN
import com.dzikoysk.sqiffy.DataType.CHAR
import com.dzikoysk.sqiffy.DataType.DATE
import com.dzikoysk.sqiffy.DataType.DATETIME
import com.dzikoysk.sqiffy.DataType.DOUBLE
import com.dzikoysk.sqiffy.DataType.FLOAT
import com.dzikoysk.sqiffy.DataType.INT
import com.dzikoysk.sqiffy.DataType.TEXT
import com.dzikoysk.sqiffy.DataType.TIMESTAMP
import com.dzikoysk.sqiffy.DataType.UUID_VARCHAR
import com.dzikoysk.sqiffy.DataType.VARCHAR
import com.dzikoysk.sqiffy.DefinitionEntry
import com.dzikoysk.sqiffy.DefinitionVersion
import com.dzikoysk.sqiffy.NULL_STRING
import com.dzikoysk.sqiffy.PropertyData
import com.dzikoysk.sqiffy.PropertyDefinitionType.ADD
import com.dzikoysk.sqiffy.PropertyDefinitionType.REMOVE
import com.dzikoysk.sqiffy.PropertyDefinitionType.RENAME
import com.dzikoysk.sqiffy.PropertyDefinitionType.RETYPE
import com.dzikoysk.sqiffy.shared.replaceFirst
import com.dzikoysk.sqiffy.toPropertyData
import java.util.ArrayDeque
import java.util.Deque
import java.util.LinkedList
import kotlin.reflect.KClass
import kotlin.reflect.full.findAnnotation

class BaseSchemeGenerator {

    data class TableAnalysisState(
        val changesToApply: Deque<DefinitionVersion>,
        var name: String,
        var properties: LinkedList<PropertyData> = LinkedList()
    )

    fun generateChangeLog(vararg classes: KClass<*>): ChangeLog =
        generateChangeLog(
            classes.map {
                DefinitionEntry(
                    packageName = it.java.packageName,
                    name = it::class.simpleName!!.substringBeforeLast("Definition"),
                    definition = it.findAnnotation()!!
                )
            }
        )

    fun generateChangeLog(tables: List<DefinitionEntry>): ChangeLog {
        val sqlGenerator = MySqlGenerator()

        val allVersions = tables.asSequence()
            .flatMap { it.definition.value.asSequence() }
            .map { it.version }
            .distinct()
            .sorted()

        val states = tables.associateWith {
            TableAnalysisState(
                changesToApply = ArrayDeque(it.definition.value.toList()),
                name = it.definition.value.first().name,
            )
        }

        val currentScheme = mutableListOf<TableAnalysisState>()
        val changeLog = linkedMapOf<String, MutableList<String>>()

        for (version in allVersions) {
            for ((definitionEntry, state) in states) {
                if (state.changesToApply.isEmpty()) {
                    continue
                }

                if (state.changesToApply.peek().version != version) {
                    continue
                }

                val changeToApply = state.changesToApply.poll()
                val changes = mutableListOf<String>()

                if (changeToApply.name != NULL_STRING && state.name != changeToApply.name) {
                    // rename table
                    changes.add(sqlGenerator.renameTable(state.name, changeToApply.name))
                    state.name = changeToApply.name
                } else if (currentScheme.none { it.name == state.name }) {
                    // create table
                    require(changeToApply.properties.all { it.definitionType == ADD })
                    val properties = changeToApply.properties.map { it.toPropertyData() }
                    changes.add(sqlGenerator.createTable(state.name, properties))
                    state.properties.addAll(properties)
                    currentScheme.add(state)
                } else {
                    // detect properties change
                    for (propertyChange in changeToApply.properties) {
                        val property = propertyChange.toPropertyData()

                        when (propertyChange.definitionType) {
                            ADD -> {
                                changes.add(sqlGenerator.createColumn(state.name, property))
                                state.properties.add(property)
                            }
                            RENAME -> {
                                changes.add(sqlGenerator.renameColumn(state.name, propertyChange.name, propertyChange.rename))
                                state.properties.replaceFirst({ it.name == propertyChange.name }, { it.copy(name = propertyChange.rename) })
                            }
                            RETYPE -> TODO()
                            REMOVE -> {
                                changes.add(sqlGenerator.removeColumn(state.name, property.name))
                                state.properties.removeIf { it.name == property.name }
                            }
                        }
                    }
                }

                changeLog.computeIfAbsent(version) { mutableListOf() }.addAll(changes)
            }
        }

        // println(baseScheme)
        return ChangeLog(changeLog)
    }

}

interface SqlGenerator {

    fun createTable(name: String, properties: List<PropertyData>): String

    fun renameTable(currentName: String, renameTo: String): String

    fun createColumn(tableName: String, property: PropertyData): String

    fun renameColumn(tableName: String, currentName: String, renameTo: String): String

    fun removeColumn(tableName: String, columnName: String): String

}

class MySqlGenerator : SqlGenerator {

    override fun createTable(name: String, properties: List<PropertyData>): String =
        """CREATE TABLE "$name" (${properties.joinToString(separator = ", ") { "\"${it.name}\" ${createDataType(it)}" }})"""

    override fun renameTable(currentName: String, renameTo: String): String =
        """ALTER TABLE "$currentName" RENAME "$renameTo""""

    override fun createColumn(tableName: String, property: PropertyData): String =
        """ALTER TABLE "$tableName" ADD "${property.name}" ${createDataType(property)}"""

    override fun renameColumn(tableName: String, currentName: String, renameTo: String): String =
        """ALTER TABLE "$tableName" RENAME COLUMN "$currentName" TO "$renameTo""""

    override fun removeColumn(tableName: String, columnName: String): String =
        """ALTER TABLE "$tableName" DROP COLUMN "$columnName""""

    private fun createDataType(property: PropertyData): String =
        with (property) {
            when (type) {
                CHAR -> "CHAR($details)"
                UUID_VARCHAR -> "VARCHAR(36)"
                VARCHAR -> "VARCHAR($details)"
                BINARY -> "BINARY($details)"
                TEXT -> "TEXT"
                BLOB -> "BLOB"
                BOOLEAN -> "BOOLEAN"
                INT -> "INT"
                FLOAT -> "FLOAT"
                DOUBLE -> "DOUBLE"
                DATE -> "DATE"
                DATETIME -> "DATETIME"
                TIMESTAMP -> "TIMESTAMP"
                else -> throw UnsupportedOperationException("Cannot create data type based on $property")
            }
        }

}