package com.dzikoysk.sqiffy.changelog.builders

import com.dzikoysk.sqiffy.changelog.ChangelogBuilder.ChangeLogGeneratorContext
import com.dzikoysk.sqiffy.definition.IndexDefinitionOperation.ADD_INDEX
import com.dzikoysk.sqiffy.definition.IndexDefinitionOperation.REMOVE_INDEX
import com.dzikoysk.sqiffy.definition.IndexType.INDEX
import com.dzikoysk.sqiffy.definition.IndexType.UNIQUE_INDEX
import com.dzikoysk.sqiffy.definition.toData

internal class ChangelogIndicesBuilder {

    fun generateIndices(context: ChangeLogGeneratorContext) {
        with(context) {
            for (index in changeToApply.indices) {
                when (index.operation) {
                    ADD_INDEX -> {
                        val indexData = index.toData()
                        checkIfConstraintOrIndexNameAlreadyUsed(index.name)

                        require(
                            value = state.indices.none { it.columns == indexData.columns },
                            lazyMessage = { "Table ${state.tableName} already has index with columns ${indexData.columns}" }
                        )

                        require(
                            value = indexData.columns.all { column -> state.properties.any { it.name == column } },
                            lazyMessage = { "Table ${state.tableName} doesn't have columns ${indexData.columns} to create index" }
                        )

                        registerChange {
                            when (indexData.type) {
                                INDEX -> "create-index-${indexData.name}" to createIndex(state.tableName, indexData.name, indexData.columns)
                                UNIQUE_INDEX -> "create-unique-index-${indexData.name}" to createUniqueIndex(state.tableName, indexData.name, indexData.columns)
                            }
                        }

                        state.indices.add(indexData)
                    }
                    REMOVE_INDEX -> {
                        val removed = state.indices.removeIf { it.name == index.name }
                        require(removed) { "Table ${state.tableName} doesn't have index to remove" }

                        registerChange {
                            "remove-index-${index.name}" to removeIndex(state.tableName, index.name)
                        }
                    }
                }
            }
        }
    }

}