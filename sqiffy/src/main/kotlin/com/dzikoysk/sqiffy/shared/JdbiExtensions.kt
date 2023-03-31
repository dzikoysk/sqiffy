package com.dzikoysk.sqiffy.shared

import com.dzikoysk.sqiffy.dsl.Column
import org.jdbi.v3.core.result.RowView

fun String.toQuoted(): String =
    when {
        this.startsWith("\"") && this.endsWith("\"") -> this
        else -> "\"$this\""
    }

fun multiline(text: String): String =
    text.trimIndent().replace("\n", " ")

operator fun <T> RowView.get(column: Column<T>): T =
    this.getColumn(column.table.getTableName() + "." + column.name, column.type)