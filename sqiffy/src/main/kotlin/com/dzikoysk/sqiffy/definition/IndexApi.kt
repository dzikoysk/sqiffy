package com.dzikoysk.sqiffy.definition

import com.dzikoysk.sqiffy.definition.IndexDefinitionOperation.ADD_INDEX

enum class IndexDefinitionOperation {
    ADD_INDEX,
    REMOVE_INDEX
}

enum class IndexType {
    INDEX,
    UNIQUE_INDEX
}

@Target()
annotation class Index(
    val operation: IndexDefinitionOperation = ADD_INDEX,
    val type: IndexType,
    val name: String,
    val columns: Array<String> = []
)

data class IndexData(
    val type: IndexType,
    val name: String,
    val columns: List<String>
)

fun Index.toData(): IndexData =
    IndexData(
        type = type,
        name = name,
        columns = columns
            .takeIf { it.isNotEmpty() }
            ?.toList()
            ?: throw IllegalArgumentException("Index $name doesn't have any columns")
    )
