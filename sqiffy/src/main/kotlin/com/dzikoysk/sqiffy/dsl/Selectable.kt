package com.dzikoysk.sqiffy.dsl

enum class SelectableType {
    COLUMN,
    AGGREGATION
}

interface Selectable {
    val selectableType: SelectableType
    val id: String
}
