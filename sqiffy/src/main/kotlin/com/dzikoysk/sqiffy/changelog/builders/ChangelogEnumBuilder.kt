package com.dzikoysk.sqiffy.changelog.builders

import com.dzikoysk.sqiffy.changelog.EnumState
import com.dzikoysk.sqiffy.changelog.Query
import com.dzikoysk.sqiffy.changelog.generator.SqlSchemeGenerator
import com.dzikoysk.sqiffy.changelog.Version
import com.dzikoysk.sqiffy.definition.DataType
import com.dzikoysk.sqiffy.definition.EnumOperation.ADD_VALUES
import com.dzikoysk.sqiffy.definition.EnumReference
import com.dzikoysk.sqiffy.definition.PropertyData
import com.dzikoysk.sqiffy.definition.TypeDefinition

class ChangelogEnumBuilder(
    private val sqlSchemeGenerator: SqlSchemeGenerator,
    private val allVersions: List<Version>
) {

    val enums = mutableListOf<EnumReference>()
    val enumStates = mutableMapOf<Version, MutableMap<TypeDefinition, EnumState>>()

    fun defineEnum(enumReference: EnumReference) {
        if (enumStates.values.any { it.containsKey(enumReference.type) }) {
            return // already defined
        }

        enums.add(enumReference)
        val enumData = enumReference.enumData
        var currentEnumState: EnumState? = null

        val allVersionsWithEnums = (allVersions + enumData.versions.map { it.version })
            .distinct()
            .sorted()

        val enumVersions = enumData.versions
            .associateBy { it.version }

        for (currentVersion in allVersionsWithEnums) {
            val enumVersion = enumVersions[currentVersion] ?: run {
                enumStates.computeIfAbsent(currentVersion) { mutableMapOf() }[enumReference.type] = currentEnumState ?: return
                return
            }
            currentEnumState = currentEnumState?.copy(values = currentEnumState.values + enumVersion.values) ?: EnumState(enumData.name, enumVersion.values)
            enumStates.computeIfAbsent(enumVersion.version) { mutableMapOf() }[enumReference.type] = currentEnumState
        }
    }

    data class EnumChangelog(
        val finalEnumStates: Map<EnumReference, EnumState>,
        val enumChangelog: Map<Version, List<Query>>
    )

    fun generateChangelog(propertiesState: Map<Version, Map<String, List<PropertyData>>>): EnumChangelog {
        val enumChangelog = linkedMapOf<Version, MutableList<Query>>()
        val latestState = mutableMapOf<EnumReference, EnumState>()

        for (enum in enums) {
            var previousState: EnumState? = null

            for (enumVersion in enum.enumData.versions) {
                val currentState = enumStates[enumVersion.version]?.get(enum.type) ?: continue
                val currentChangelog = enumChangelog.computeIfAbsent(enumVersion.version) { mutableListOf() }

                when {
                    previousState == null -> {
                        val createEnumQuery = sqlSchemeGenerator.createEnum(
                            name = enum.enumData.name,
                            values = enumVersion.values.toList()
                        )

                        if (createEnumQuery != null) {
                            currentChangelog.add(createEnumQuery)
                        }
                    }
                    enumVersion.operation == ADD_VALUES -> {
                        val addEnumQuery = sqlSchemeGenerator.addEnumValues(
                            enum = currentState,
                            values = enumVersion.values.toList(),
                            inUse = propertiesState[enumVersion.version]
                                ?.flatMap { (table, properties) -> properties.map { table to it } }
                                ?.filter { (_, property) -> property.type == DataType.ENUM }
                                ?: emptyList()
                        )

                        if (addEnumQuery != null) {
                            currentChangelog.add(addEnumQuery)
                        }
                    }
                    else -> throw IllegalStateException("Unsupported operation ${enumVersion.operation}")
                }

                previousState = currentState
            }

            latestState[enum] = previousState ?: continue
        }

        return EnumChangelog(
            finalEnumStates = latestState,
            enumChangelog = enumChangelog
        )
    }

}