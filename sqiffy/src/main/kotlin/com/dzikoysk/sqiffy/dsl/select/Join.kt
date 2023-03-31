package com.dzikoysk.sqiffy.dsl.select

import com.dzikoysk.sqiffy.dsl.Column

enum class JoinType {
    INNER,
    LEFT,
    RIGHT,
    FULL
}

data class Join(
    val type: JoinType,
    val on: Column<*>,
    val onTo: Column<*>
)