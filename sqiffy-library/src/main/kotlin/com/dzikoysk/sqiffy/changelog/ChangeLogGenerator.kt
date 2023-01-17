package com.dzikoysk.sqiffy.changelog

import com.dzikoysk.sqiffy.ConstraintData
import com.dzikoysk.sqiffy.DefinitionEntry
import com.dzikoysk.sqiffy.DefinitionVersion
import com.dzikoysk.sqiffy.IndexData
import com.dzikoysk.sqiffy.PropertyData
import com.dzikoysk.sqiffy.sql.MySqlGenerator
import com.dzikoysk.sqiffy.sql.SqlGenerator
import java.util.ArrayDeque
import java.util.Deque
import kotlin.reflect.KClass
import kotlin.reflect.full.findAnnotation

internal data class TableAnalysisState(
    val changesToApply: Deque<DefinitionVersion>,
    var name: String,
    val properties: MutableList<PropertyData> = mutableListOf(),
    val constraints: MutableList<ConstraintData> = mutableListOf(),
    val indices: MutableList<IndexData> = mutableListOf()
)

internal data class ChangeLogGeneratorContext(
    val sqlGenerator: SqlGenerator,
    val currentScheme: MutableList<TableAnalysisState>,
    val changeToApply: DefinitionVersion,
    val changes: MutableList<String> = mutableListOf(),
    val state: TableAnalysisState
) {

    fun registerChange(change: String) = changes.add(change)

}

class ChangeLogGenerator {

    private val changeLogPropertiesGenerator = ChangeLogPropertiesGenerator()
    private val changeLogConstraintsGenerator = ChangeLogConstraintsGenerator()
    private val changeLogIndicesGenerator = ChangeLogIndicesGenerator()
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
                when {
                    state.changesToApply.isEmpty() -> continue
                    state.changesToApply.peek().version != version ->  continue
                }

                val context = ChangeLogGeneratorContext(
                    sqlGenerator = sqlGenerator,
                    currentScheme = currentScheme,
                    changeToApply = state.changesToApply.poll(),
                    state = state
                )

                changeLogPropertiesGenerator.generateProperties(context)
                changeLogConstraintsGenerator.generateConstraints(context)
                changeLogIndicesGenerator.generateIndices(context)

                val changes = changeLog.computeIfAbsent(version) { mutableListOf() }
                changes.addAll(context.changes)
            }
        }

        return ChangeLog(changeLog)
    }

}