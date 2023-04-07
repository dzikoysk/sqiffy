package com.dzikoysk.sqiffy.dsl

import com.dzikoysk.sqiffy.shared.get
import org.jdbi.v3.core.result.RowView

interface Statement

class Row(val view: RowView) {
    operator fun <T> get(column: Column<T>): T = view[column]
}
