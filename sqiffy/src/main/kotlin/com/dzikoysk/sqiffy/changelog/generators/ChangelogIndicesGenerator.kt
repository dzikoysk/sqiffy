package com.dzikoysk.sqiffy.changelog.generators

import com.dzikoysk.sqiffy.changelog.ChangeLogGeneratorContext
import com.dzikoysk.sqiffy.definition.IndexData
import com.dzikoysk.sqiffy.definition.IndexDefinitionOperation.ADD_INDEX
import com.dzikoysk.sqiffy.definition.IndexDefinitionOperation.REMOVE_INDEX
import com.dzikoysk.sqiffy.definition.IndexType.INDEX
import com.dzikoysk.sqiffy.definition.IndexType.UNIQUE_INDEX

internal class ChangelogIndicesGenerator {

    fun generateIndices(context: ChangeLogGeneratorContext) {
        with(context) {
            for (index in changeToApply.indices) {
                when (index.operation) {
                    ADD_INDEX -> {
                        val indexData = IndexData(
                            type = index.type,
                            name = index.name,
                            columns = index.columns
                                .takeIf { it.isNotEmpty() }
                                ?.toList()
                                ?: throw IllegalArgumentException("Index ${index.name} doesn't have any columns")
                        )

                        require(
                            value = state.indices.none { it.name == indexData.name },
                            lazyMessage = { "Table ${state.tableName} already has index with name ${indexData.name}" }
                        )

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
                                INDEX -> createIndex(state.tableName, indexData.name, indexData.columns)
                                UNIQUE_INDEX -> createUniqueIndex(state.tableName, indexData.name, indexData.columns)
                            }
                        }

                        state.indices.add(indexData)
                    }
                    REMOVE_INDEX -> {
                        val removed = state.indices.removeIf { it.name == index.name }
                        require(removed) { "Table ${state.tableName} doesn't have index to remove" }

                        registerChange {
                            removeIndex(state.tableName, index.name)
                        }
                    }
                }
            }
        }
    }

}