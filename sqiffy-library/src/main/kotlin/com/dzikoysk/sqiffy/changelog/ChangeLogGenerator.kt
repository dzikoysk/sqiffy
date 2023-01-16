package com.dzikoysk.sqiffy.changelog

import com.dzikoysk.sqiffy.DefinitionEntry
import com.dzikoysk.sqiffy.DefinitionVersion
import com.dzikoysk.sqiffy.NULL_STRING
import com.dzikoysk.sqiffy.PropertyData
import com.dzikoysk.sqiffy.PropertyDefinitionType.ADD
import com.dzikoysk.sqiffy.PropertyDefinitionType.REMOVE
import com.dzikoysk.sqiffy.PropertyDefinitionType.RENAME
import com.dzikoysk.sqiffy.PropertyDefinitionType.RETYPE
import com.dzikoysk.sqiffy.shared.replaceFirst
import com.dzikoysk.sqiffy.sql.MySqlGenerator
import com.dzikoysk.sqiffy.toPropertyData
import java.util.ArrayDeque
import java.util.Deque
import java.util.LinkedList
import kotlin.reflect.KClass
import kotlin.reflect.full.findAnnotation

private data class TableAnalysisState(
    val changesToApply: Deque<DefinitionVersion>,
    var name: String,
    var properties: LinkedList<PropertyData> = LinkedList()
)

private data class ChangeLogGeneratorContext(
    val currentScheme: MutableList<TableAnalysisState>,
    val changeToApply: DefinitionVersion,
    val changes: MutableList<String> = mutableListOf(),
    val state: TableAnalysisState
)

class ChangeLogGenerator {

    private val sqlGenerator = MySqlGenerator()

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

                val context = ChangeLogGeneratorContext(
                    currentScheme = currentScheme,
                    changeToApply = state.changesToApply.poll(),
                    state = state
                )

                generateProperties(context)
                generateConstraints(context)
                generateIndices(context)

                changeLog.computeIfAbsent(version) { mutableListOf() }.addAll(context.changes)
            }
        }

        // println(baseScheme)
        return ChangeLog(changeLog)
    }

    private fun generateProperties(context: ChangeLogGeneratorContext) {
        with(context) {
            when {
                changeToApply.name != NULL_STRING && state.name != changeToApply.name -> {
                    // rename table
                    changes.add(sqlGenerator.renameTable(state.name, changeToApply.name))
                    state.name = changeToApply.name
                }
                currentScheme.none { it.name == state.name } -> {
                    // create a new table
                    require(changeToApply.properties.all { it.definitionType == ADD })
                    val properties = changeToApply.properties.map { it.toPropertyData() }
                    changes.add(sqlGenerator.createTable(state.name, properties))
                    state.properties.addAll(properties)
                    currentScheme.add(state)
                    return // properties are up-to-date
                }
            }

            // detect properties change in existing table
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
                    RETYPE -> {
                        // SQLite may not support this

                    }
                    REMOVE -> {
                        changes.add(sqlGenerator.removeColumn(state.name, property.name))
                        state.properties.removeIf { it.name == property.name }
                    }
                }
            }
        }
    }

    private fun generateConstraints(context: ChangeLogGeneratorContext) {
        with(context) {
            for (constraint in changeToApply.constraints) {

            }
        }
    }

    private fun generateIndices(context: ChangeLogGeneratorContext) {
        with(context) {
            for (index in changeToApply.indices) {

            }
        }
    }

}