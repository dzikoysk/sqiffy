package com.dzikoysk.sqiffy.changelog

import com.dzikoysk.sqiffy.changelog.generators.ChangelogConstraintsGenerator
import com.dzikoysk.sqiffy.changelog.generators.ChangelogEnumGenerator
import com.dzikoysk.sqiffy.changelog.generators.ChangelogIndicesGenerator
import com.dzikoysk.sqiffy.changelog.generators.ChangelogPropertiesGenerator
import com.dzikoysk.sqiffy.definition.DataType
import com.dzikoysk.sqiffy.definition.DefinitionEntry
import com.dzikoysk.sqiffy.definition.DefinitionVersion
import com.dzikoysk.sqiffy.definition.PropertyData
import com.dzikoysk.sqiffy.definition.TypeFactory
import java.util.ArrayDeque
import kotlin.reflect.KClass
import kotlin.reflect.full.findAnnotation

class ChangeLogGenerator(
    private val sqlSchemeGenerator: SqlSchemeGenerator,
    private val typeFactory: TypeFactory
) {

    private val changeLogPropertiesGenerator = ChangelogPropertiesGenerator()
    private val changeLogConstraintsGenerator = ChangelogConstraintsGenerator()
    private val changeLogIndicesGenerator = ChangelogIndicesGenerator()

    internal data class ChangeLogGeneratorContext(
        val typeFactory: TypeFactory,
        val sqlSchemeGenerator: SqlSchemeGenerator,
        val currentEnums: Enums,
        val currentScheme: MutableList<TableAnalysisState>,
        val changeToApply: DefinitionVersion,
        val changes: MutableList<String> = mutableListOf(),
        val state: TableAnalysisState
    ) {

        fun registerChange(change: String) =
            changes.add(change)

        fun registerChange(supplier: SqlSchemeGenerator.() -> String) =
            registerChange(supplier.invoke(sqlSchemeGenerator))

        fun checkIfConstraintOrIndexNameAlreadyUsed(name: String) =
            currentScheme
                .flatMap { table ->
                    val constraints = table.constraints.map { table.tableName to it }
                    val indices = table.indices.map { table.tableName to it.name }
                    constraints + indices
                }
                .firstOrNull { (_, constraint) -> constraint == name }
                ?.also { (table, constraint) ->
                    throw IllegalStateException("Constraint (or Index) with name '${constraint}' defined in table '${this.state.tableName}' already exists in table '$table'")
                }
    }

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
        val propertiesState = mutableMapOf<Version, MutableMap<String, List<PropertyData>>>()

        val tableStates = tables.associateWith {
            TableAnalysisState(
                changesToApply = ArrayDeque(it.definition.value.toList()),
                source = it.source,
                tableName = it.definition.value.firstOrNull()?.name ?: throw IllegalStateException("Class ${it.source} has @Definition annotation without any scheme version"),
            )
        }

        val allVersions = tables.asSequence()
            .flatMap { it.definition.value.asSequence() }
            .map { it.version }
            .distinct()
            .sorted()

        val changelogEnumGenerator = ChangelogEnumGenerator(
            sqlSchemeGenerator = sqlSchemeGenerator,
            allVersions = allVersions.toList()
        )

        val schemeChangelog = linkedMapOf<Version, MutableList<Query>>()
        val currentScheme = mutableListOf<TableAnalysisState>()

        for (version in allVersions) {
            val changes = schemeChangelog.computeIfAbsent(version) { mutableListOf() }
            val contexts = mutableListOf<ChangeLogGeneratorContext>()
            val constraints = mutableListOf<Runnable>()
            val indices = mutableListOf<Runnable>()

            for ((_, state) in tableStates) {
                when {
                    state.changesToApply.isEmpty() -> continue
                    state.changesToApply.peek().version != version -> continue
                }

                val baseContext = ChangeLogGeneratorContext(
                    typeFactory = typeFactory,
                    sqlSchemeGenerator = sqlSchemeGenerator,
                    currentEnums = Enums(
                        availableEnums = { changelogEnumGenerator.enumStates[version] ?: emptyMap() },
                        defineEnum = { changelogEnumGenerator.defineEnum(it) }
                    ),
                    currentScheme = currentScheme,
                    changeToApply = state.changesToApply.poll(),
                    state = state
                )

                val propertiesContext = baseContext.copy(changes = mutableListOf())
                changeLogPropertiesGenerator.generateProperties(propertiesContext)
                contexts.add(propertiesContext)
                propertiesState.computeIfAbsent(version) { mutableMapOf() }[state.tableName] = propertiesContext.state.properties.toList()

                if (state.properties.count { it.type == DataType.SERIAL } > 1) {
                    throw IllegalStateException("Table '${state.tableName}' has more than one SERIAL column")
                }

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

        val (latestEnumState, enumChangelog) = changelogEnumGenerator.generateChangelog(
            propertiesState = propertiesState
        )

        return ChangeLog(
            enums = latestEnumState,
            tables = tableStates.mapValues { it.value.tableName },
            enumChanges =
                enumChangelog.map { (version, changes) ->
                    SchemeChange(
                        version = version,
                        changes = changes.map { Change(it) }
                    )
                },
            schemeChanges =
                schemeChangelog.map { (version, changes) ->
                    SchemeChange(
                        version = version,
                        changes = changes.map { Change(it) }
                    )
                }
        )
    }

}