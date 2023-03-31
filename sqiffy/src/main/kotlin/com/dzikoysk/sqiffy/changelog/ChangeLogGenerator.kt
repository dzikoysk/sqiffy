package com.dzikoysk.sqiffy.changelog

import com.dzikoysk.sqiffy.definition.ConstraintData
import com.dzikoysk.sqiffy.definition.DefinitionEntry
import com.dzikoysk.sqiffy.definition.DefinitionVersion
import com.dzikoysk.sqiffy.definition.IndexData
import com.dzikoysk.sqiffy.definition.PropertyData
import com.dzikoysk.sqiffy.definition.TypeFactory
import java.util.ArrayDeque
import java.util.Deque
import kotlin.reflect.KClass
import kotlin.reflect.full.findAnnotation

internal data class TableAnalysisState(
    val changesToApply: Deque<DefinitionVersion>,
    val source: String,
    var tableName: String,
    val properties: MutableList<PropertyData> = mutableListOf(),
    val constraints: MutableList<ConstraintData> = mutableListOf(),
    val indices: MutableList<IndexData> = mutableListOf()
)

internal data class ChangeLogGeneratorContext(
    val typeFactory: TypeFactory,
    val sqlSchemeGenerator: SqlSchemeGenerator,
    val currentScheme: MutableList<TableAnalysisState>,
    val changeToApply: DefinitionVersion,
    val changes: MutableList<String> = mutableListOf(),
    val state: TableAnalysisState
) {

    fun registerChange(change: String) = changes.add(change)
    fun registerChange(supplier: SqlSchemeGenerator.() -> String) = registerChange(supplier.invoke(sqlSchemeGenerator))

}

class ChangeLogGenerator(
    private val sqlSchemeGenerator: SqlSchemeGenerator,
    private val typeFactory: TypeFactory
) {

    private val changeLogPropertiesGenerator = ChangeLogPropertiesGenerator()
    private val changeLogConstraintsGenerator = ChangeLogConstraintsGenerator()
    private val changeLogIndicesGenerator = ChangeLogIndicesGenerator()

    fun generateChangeLog(vararg classes: KClass<*>): ChangeLog =
        generateChangeLog(
            classes.map {
                DefinitionEntry(
                    source = it.qualifiedName!!,
                    packageName = it.java.`package`.name,
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
                source = it.source,
                tableName = it.definition.value.firstOrNull()?.name ?: throw IllegalStateException("Class ${it.source} has @Definition annotation without any scheme version"),
            )
        }

        val currentScheme = mutableListOf<TableAnalysisState>()
        val changeLog = linkedMapOf<Version, MutableList<Query>>()

        for (version in allVersions) {
            val changes = changeLog.computeIfAbsent(version) { mutableListOf() }
            val contexts = mutableListOf<ChangeLogGeneratorContext>()
            val constraints = mutableListOf<Runnable>()
            val indices = mutableListOf<Runnable>()

            for ((_, state) in states) {
                when {
                    state.changesToApply.isEmpty() -> continue
                    state.changesToApply.peek().version != version ->  continue
                }

                val baseContext = ChangeLogGeneratorContext(
                    typeFactory = typeFactory,
                    sqlSchemeGenerator = sqlSchemeGenerator,
                    currentScheme = currentScheme,
                    changeToApply = state.changesToApply.poll(),
                    state = state
                )

                val propertiesContext = baseContext.copy(changes = mutableListOf())
                changeLogPropertiesGenerator.generateProperties(propertiesContext)
                contexts.add(propertiesContext)

                constraints.add {
                    val constraintsContext = baseContext.copy(changes = mutableListOf())
                    changeLogConstraintsGenerator.generateConstraints(constraintsContext)
                    contexts.add(constraintsContext)
                }

                indices.add {
                    val indicesContext = baseContext.copy(changes = mutableListOf())
                    changeLogIndicesGenerator.generateIndices(indicesContext)
                    contexts.add(indicesContext)
                }
            }

            constraints.forEach { it.run() }
            indices.forEach { it.run() }
            contexts.forEach { changes.addAll(it.changes) }
        }

        return changeLog
            .map { (version, changes) ->
                VersionChange(
                    version = version,
                    changes = changes.map { Change(it) }
                )
            }
            .let { changes ->
                ChangeLog(
                    tables = states.mapValues { it.value.tableName },
                    changes = changes
                )
            }
    }

}