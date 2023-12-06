package com.dzikoysk.sqiffy.changelog.builders

import com.dzikoysk.sqiffy.changelog.Change
import com.dzikoysk.sqiffy.changelog.EnumState
import com.dzikoysk.sqiffy.changelog.Version
import com.dzikoysk.sqiffy.changelog.generator.SqlSchemeGenerator
import com.dzikoysk.sqiffy.definition.DataType
import com.dzikoysk.sqiffy.definition.EnumDefinitionData
import com.dzikoysk.sqiffy.definition.EnumOperation.ADD_VALUES
import com.dzikoysk.sqiffy.definition.PropertyData
import com.dzikoysk.sqiffy.definition.TypeDefinition

class ChangelogEnumBuilder(
    private val sqlSchemeGenerator: SqlSchemeGenerator,
    private val allVersions: List<Version> = emptyList()
) {

    val enums = mutableListOf<EnumDefinitionData>()
    val enumStates = mutableMapOf<Version, MutableMap<TypeDefinition, EnumState>>()

    fun defineEnum(enumData: EnumDefinitionData) {
        val enumType = enumData.getMappedTypeDefinition()

        if (enumStates.values.any { it.containsKey(enumType) }) {
            return // already defined
        }

        enums.add(enumData)
        var currentEnumState: EnumState? = null

        val allVersionsWithEnums = (allVersions + enumData.versions.map { it.version })
            .distinct()
            .sorted()

        val enumVersions = enumData.versions
            .associateBy { it.version }

        for (currentVersion in allVersionsWithEnums) {
            val enumVersion = enumVersions[currentVersion]

            if (enumVersion == null) {
                enumStates.computeIfAbsent(currentVersion) { mutableMapOf() }[enumType] = currentEnumState ?: continue
            } else {
                currentEnumState = currentEnumState?.copy(values = currentEnumState.values + enumVersion.values) ?: EnumState(enumData.name, enumVersion.values)
                enumStates.computeIfAbsent(enumVersion.version) { mutableMapOf() }[enumType] = currentEnumState
            }
        }
    }

    data class EnumChangelog(
        val finalEnumStates: Map<EnumDefinitionData, EnumState>,
        val enumChangelog: Map<Version, List<Change>>
    )

    fun generateChangelog(propertiesState: Map<Version, Map<String, List<PropertyData>>>): EnumChangelog {
        val enumChangelog = linkedMapOf<Version, MutableList<Change>>()
        val latestState = mutableMapOf<EnumDefinitionData, EnumState>()

        for (enumData in enums) {
            var previousState: EnumState? = null
            val enumType = enumData.getMappedTypeDefinition()

            for (enumVersion in enumData.versions) {
                val currentState = enumStates[enumVersion.version]?.get(enumType) ?: continue
                val currentChangelog = enumChangelog.computeIfAbsent(enumVersion.version) { mutableListOf() }

                when {
                    previousState == null -> {
                        val createEnumQuery = sqlSchemeGenerator.createEnum(
                            name = enumData.name,
                            values = enumVersion.values.toList()
                        )

                        if (createEnumQuery != null) {
                            currentChangelog.add(Change(
                                description = "create-enum-${enumData.name}",
                                query = createEnumQuery
                            ))
                        }
                    }
                    enumVersion.operation == ADD_VALUES -> {
                        val addEnumQuery = sqlSchemeGenerator.addEnumValues(
                            enum = currentState,
                            values = enumVersion.values.toList(),
                            inUse = propertiesState[enumVersion.version]
                                ?.flatMap { (table, properties) -> properties.map { table to it } }
                                ?.filter { (_, property) -> property.type == DataType.ENUM && property.enumDefinition!!.mappedTo == enumData.mappedTo }
                                ?: emptyList()
                        )

                        if (addEnumQuery != null) {
                            currentChangelog.add(Change(
                                description = "add-enum-values-${enumData.name}",
                                query = addEnumQuery
                            ))
                        }
                    }
                    else -> throw IllegalStateException("Unsupported operation ${enumVersion.operation}")
                }

                previousState = currentState
            }

            latestState[enumData] = previousState ?: continue
        }

        return EnumChangelog(
            finalEnumStates = latestState,
            enumChangelog = enumChangelog
        )
    }

}