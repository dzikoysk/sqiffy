package com.dzikoysk.sqiffy.dsl

import com.dzikoysk.sqiffy.shared.get
import org.jdbi.v3.core.JdbiException
import org.jdbi.v3.core.result.RowView

interface Statement

class RowException(val selectable: Selectable, cause: Throwable? = null) : RuntimeException(cause)

class Row(
    val view: RowView,
    val autogeneratedKey: Column<*>? = null
) {

    operator fun <T> get(column: Column<T>): T =
        try {
            when (column) {
                autogeneratedKey -> view.getColumn(1, column.type)
                else -> view[column]
            }
        } catch (jdbiException: JdbiException) {
            throw RowException(column, jdbiException)
        }

    operator fun <T> get(aggregation: Aggregation<T>): T =
        try {
            view[aggregation]
        } catch (jdbiException: JdbiException) {
            throw RowException(aggregation, jdbiException)
        }

}

class Values {

    private val values: MutableMap<Column<*>, Any?> = mutableMapOf()

    operator fun <T : Any?> set(column: Column<T>, value: T) {
        values[column] = value
    }

    fun getColumn(column: String): Column<*>? =
        values.entries
            .firstOrNull { it.key.name == column }
            .let { it?.key }

    fun getValue(column: Column<*>): Any? =
        values[column]

    fun getColumns(): Set<Column<*>> =
        values.keys

    fun getValues(): Map<Column<*>, Any?> =
        values

}