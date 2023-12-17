package com.dzikoysk.sqiffy.changelog

import com.dzikoysk.sqiffy.changelog.builders.ChangelogConstraintsBuilder
import com.dzikoysk.sqiffy.changelog.builders.ChangelogEnumBuilder
import com.dzikoysk.sqiffy.changelog.builders.ChangelogIndicesBuilder
import com.dzikoysk.sqiffy.changelog.builders.ChangelogPropertiesBuilder
import com.dzikoysk.sqiffy.changelog.builders.VersionedFunctionBuilder
import com.dzikoysk.sqiffy.changelog.generator.SqlSchemeGenerator
import com.dzikoysk.sqiffy.definition.DataType
import com.dzikoysk.sqiffy.definition.Definition
import com.dzikoysk.sqiffy.definition.DefinitionVersionData
import com.dzikoysk.sqiffy.definition.FunctionDefinition
import com.dzikoysk.sqiffy.definition.FunctionDefinitionData
import com.dzikoysk.sqiffy.definition.NamingStrategy
import com.dzikoysk.sqiffy.definition.ParsedDefinition
import com.dzikoysk.sqiffy.definition.PropertyData
import com.dzikoysk.sqiffy.definition.RuntimeTypeFactory
import com.dzikoysk.sqiffy.definition.TypeFactory
import com.dzikoysk.sqiffy.definition.toData
import com.dzikoysk.sqiffy.definition.toFunctionData
import java.util.ArrayDeque
import kotlin.reflect.KClass
import kotlin.reflect.KProperty
import kotlin.reflect.full.findAnnotation
import kotlin.reflect.full.findAnnotations

class ChangelogBuilder(
    private val sqlSchemeGenerator: SqlSchemeGenerator,
    private val namingStrategy: NamingStrategy,
    private val typeFactory: TypeFactory = RuntimeTypeFactory(),
) {

    private val propertiesBuilder = ChangelogPropertiesBuilder()
    private val constraintsBuilder = ChangelogConstraintsBuilder()
    private val indicesBuilder = ChangelogIndicesBuilder()

    internal data class ChangeLogGeneratorContext(
        val typeFactory: TypeFactory,
        val sqlSchemeGenerator: SqlSchemeGenerator,
        val currentEnums: Enums,
        val currentScheme: MutableList<TableAnalysisState>,
        val changeToApply: DefinitionVersionData,
        val changes: MutableList<Change> = mutableListOf(),
        val state: TableAnalysisState
    ) {

        fun registerChange(description: String, query: String) =
            query
                .trim()
                .takeIf { it.isNotEmpty() }
                ?.let { changes.add(Change(description = description, query = query)) }

        fun registerChange(supplier: SqlSchemeGenerator.() -> Pair<String, String>) =
            supplier.invoke(sqlSchemeGenerator).let {
                registerChange(description = it.first, query = it.second)
            }

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

    fun generateChangeLogAtRuntime(functions: Collection<KProperty<*>> = emptyList(), tables: Collection<KClass<*>>): Changelog =
        generateChangeLog(
            functions = functions
                .toSet()
                .flatMap { it.findAnnotations<FunctionDefinition>() }
                .map { it.toFunctionData() },
            tables = tables
                .toSet()
                .map {
                    ParsedDefinition(
                        source = it.qualifiedName!!,
                        packageName = it.java.`package`.name,
                        name = it::class.simpleName!!.substringBeforeLast("Definition"),
                        definition = it.findAnnotation<Definition>()!!.toData()
                    )
                }
        )

    fun generateChangeLog(functions: List<FunctionDefinitionData>, tables: List<ParsedDefinition>): Changelog {
        val propertiesState = mutableMapOf<Version, MutableMap<String, List<PropertyData>>>()

        val tableStates = tables.associateWith {
            TableAnalysisState(
                changesToApply = ArrayDeque(it.definition.versions),
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

        val schemeChangelog = linkedMapOf<Version, MutableList<Change>>()
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
                propertiesBuilder.generateProperties(propertiesContext, namingStrategy)
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

        val (latestFunctionState, functionChangelog) = VersionedFunctionBuilder(sqlSchemeGenerator).generateFunctionChangelog(
            definitions = functions
        )

        return Changelog(
            enums = latestEnumState,
            functions = latestFunctionState,
            tables = tableStates.mapValues { it.value.tableName },
            enumChanges =
                enumChangelog.map { (version, changes) ->
                    SchemeChange(
                        version = version,
                        changes = changes
                    )
                },
            functionChanges =
                functionChangelog.map { (version, changes) ->
                    SchemeChange(
                        version = version,
                        changes = changes
                    )
                },
            schemeChanges =
                schemeChangelog.map { (version, changes) ->
                    SchemeChange(
                        version = version,
                        changes = changes
                    )
                }
        )
    }

}