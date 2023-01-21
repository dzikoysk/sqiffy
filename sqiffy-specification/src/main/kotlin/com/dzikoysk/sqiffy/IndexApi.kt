package com.dzikoysk.sqiffy

enum class IndexDefinitionType {
    ADD,
    REMOVE
}

enum class IndexType {
    INDEX,
    UNIQUE_INDEX
}

@Target()
annotation class Index(
    val definitionType: IndexDefinitionType = IndexDefinitionType.ADD,
    val type: IndexType,
    val columns: Array<String>
)

data class IndexData(
    val type: IndexType,
    val columns: List<String>
)