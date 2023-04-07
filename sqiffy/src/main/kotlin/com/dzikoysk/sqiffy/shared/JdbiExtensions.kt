package com.dzikoysk.sqiffy.shared

import com.dzikoysk.sqiffy.dsl.Aggregation
import com.dzikoysk.sqiffy.dsl.Column
import org.jdbi.v3.core.mapper.MappingException
import org.jdbi.v3.core.result.RowView

fun String.toQuoted(): String =
    when {
        this.startsWith("\"") && this.endsWith("\"") -> this
        else -> "\"$this\""
    }

fun multiline(text: String): String =
    text.trimIndent().replace("\n", " ").trim()

operator fun <T> RowView.get(column: Column<T>): T =
    try {
        getColumn(column.table.getTableName() + "." + column.name, column.type) // get column from generated alias
    } catch (mappingException: MappingException) {
        getColumn(column.name, column.type) // default column name fallback
    }

operator fun <T> RowView.get(aggregation: Aggregation<T>): T =
    with (aggregation) {
        try {
            getColumn("${type.aggregationFunction}(${column.table.getTableName()}.${column.name})", resultType) // get column from generated alias
        } catch (mappingException: MappingException) {
            getColumn("${type.aggregationFunction}(${column.name})", resultType) // default column name fallback
        }
    }