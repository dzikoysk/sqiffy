package com.dzikoysk.sqiffy.changelog.builders

import com.dzikoysk.sqiffy.changelog.Change
import com.dzikoysk.sqiffy.changelog.Version
import com.dzikoysk.sqiffy.changelog.generator.SqlSchemeGenerator
import com.dzikoysk.sqiffy.definition.FunctionDefinitionData
import com.dzikoysk.sqiffy.definition.FunctionVersionData

data class FunctionChangelog(
    val finalFunctionState: Map<FunctionDefinitionData, FunctionVersionData>,
    val changelog: Map<Version, List<Change>>
)

internal class VersionedFunctionBuilder(
    private val sqlSchemeGenerator: SqlSchemeGenerator,
) {

    fun generateFunctionChangelog(definitions: List<FunctionDefinitionData>): FunctionChangelog {
        val changelog = mutableMapOf<Version, MutableList<Change>>()

        definitions.forEach { definition ->
            definition.versions.forEach { functionVersion ->
                val changes = changelog.computeIfAbsent(functionVersion.version) { mutableListOf() }

                changes.add(
                    Change(
                        description = "create-function-${definition.name}",
                        query = sqlSchemeGenerator.createFunction(
                            name = definition.name,
                            parameters = functionVersion.parameters.toTypedArray(),
                            returnType = functionVersion.returnType,
                            body = functionVersion.content
                        )
                    )
                )
            }
        }

        return FunctionChangelog(
            finalFunctionState = definitions.associateWith { it.versions.last() },
            changelog = changelog
        )
    }

}