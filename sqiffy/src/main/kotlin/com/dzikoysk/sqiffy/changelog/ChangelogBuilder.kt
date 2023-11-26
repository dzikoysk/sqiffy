package com.dzikoysk.sqiffy.changelog

import com.dzikoysk.sqiffy.changelog.builders.ChangelogConstraintsBuilder
import com.dzikoysk.sqiffy.changelog.builders.ChangelogEnumBuilder
import com.dzikoysk.sqiffy.changelog.builders.ChangelogIndicesBuilder
import com.dzikoysk.sqiffy.changelog.builders.ChangelogPropertiesBuilder
import com.dzikoysk.sqiffy.changelog.generator.SqlSchemeGenerator
import com.dzikoysk.sqiffy.definition.DataType
import com.dzikoysk.sqiffy.definition.DefinitionEntry
import com.dzikoysk.sqiffy.definition.DefinitionVersion
import com.dzikoysk.sqiffy.definition.PropertyData
import com.dzikoysk.sqiffy.definition.RuntimeTypeFactory
import com.dzikoysk.sqiffy.definition.TypeFactory
import java.util.ArrayDeque
import kotlin.reflect.KClass
import kotlin.reflect.full.findAnnotation

class ChangelogBuilder(
    private val sqlSchemeGenerator: SqlSchemeGenerator,
    private val typeFactory: TypeFactory = RuntimeTypeFactory()
) {

    private val propertiesBuilder = ChangelogPropertiesBuilder()
    private val constraintsBuilder = ChangelogConstraintsBuilder()
    private val indicesBuilder = ChangelogIndicesBuilder()

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
            change
                .trim()
                .takeIf { it.isNotEmpty() }
                ?.let { changes.add(it) }

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
            classes
                .toSet()
                .map {
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
                changesToApply = ArrayDeque(it.definition.versions.toList()),
                source = it.source,
                tableName = it.definition.versions
                    .firstOrNull()
                    ?.name
                    ?: throw IllegalStateException("Class ${it.source} has @Definition annotation without any scheme version"),
            )
        }

        val allVersions = tables.asSequence()
            .flatMap { it.definition.versions.asSequence() }
            .map { it.version }
            .distinct()
            .sorted()

        val changelogEnumBuilder = ChangelogEnumBuilder(
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
                        availableEnums = { changelogEnumBuilder.enumStates[version] ?: emptyMap() },
                        defineEnum = { changelogEnumBuilder.defineEnum(it) }
                    ),
                    currentScheme = currentScheme,
                    changeToApply = state.changesToApply.poll(),
                    state = state
                )

                val propertiesContext = baseContext.copy(changes = mutableListOf())
                propertiesBuilder.generateProperties(propertiesContext)
                contexts.add(propertiesContext)
                propertiesState.computeIfAbsent(version) { mutableMapOf() }[state.tableName] = propertiesContext.state.properties.toList()

                if (state.properties.count { it.type == DataType.SERIAL } > 1) {
                    throw IllegalStateException("Table '${state.tableName}' has more than one SERIAL column")
                }

                constraints.add {
                    val constraintsContext = baseContext.copy(changes = mutableListOf())
                    constraintsBuilder.generateConstraints(constraintsContext)
                    contexts.add(constraintsContext)
                }

                indices.add {
                    val indicesContext = baseContext.copy(changes = mutableListOf())
                    indicesBuilder.generateIndices(indicesContext)
                    contexts.add(indicesContext)
                }
            }

            constraints.forEach { it.run() }
            indices.forEach { it.run() }
            contexts.forEach { changes.addAll(it.changes) }
        }

        val (latestEnumState, enumChangelog) = changelogEnumBuilder.generateChangelog(
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