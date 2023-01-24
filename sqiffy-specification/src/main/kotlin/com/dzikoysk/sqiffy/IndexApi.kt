package com.dzikoysk.sqiffy

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
    val operation: IndexDefinitionOperation = IndexDefinitionOperation.ADD_INDEX,
    val type: IndexType,
    val name: String,
    val columns: Array<String> = []
)

data class IndexData(
    val type: IndexType,
    val name: String,
    val columns: List<String>
)