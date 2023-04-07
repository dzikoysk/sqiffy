package com.dzikoysk.sqiffy.dsl.statements

import com.dzikoysk.sqiffy.SqiffyDatabase
import com.dzikoysk.sqiffy.dsl.Column
import com.dzikoysk.sqiffy.dsl.Expression
import com.dzikoysk.sqiffy.dsl.Row
import com.dzikoysk.sqiffy.dsl.Selectable
import com.dzikoysk.sqiffy.dsl.Statement
import com.dzikoysk.sqiffy.dsl.Table
import com.dzikoysk.sqiffy.dsl.generator.ParameterAllocator
import com.dzikoysk.sqiffy.dsl.generator.bindArguments
import org.slf4j.event.Level

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

open class SelectStatement(
    protected val database: SqiffyDatabase,
    protected val from: Table,
) : Statement {

    protected var slice: MutableList<Selectable> = from.getColumns().toMutableList()
    protected val joins: MutableList<Join> = mutableListOf()
    protected var where: Expression<Boolean>? = null

    fun where(where: () -> Expression<Boolean>): SelectStatement = also {
        this.where = where()
    }

    fun slice(vararg column: Selectable): SelectStatement = also {
        this.slice = column.toMutableList()
    }

    fun <T> join(type: JoinType, on: Column<T>, to: Column<T>): SelectStatement = also {
        joins.add(Join(type, on, to))
        slice.addAll(to.table.getColumns())
    }

    fun <R> map(mapper: (Row) -> R): Sequence<R> =
        database.getJdbi().withHandle<Sequence<R>, Exception> { handle ->
            val allocator = ParameterAllocator()

            val expression = where?.let {
                database.sqlQueryGenerator.createExpression(
                    allocator = allocator,
                    expression = where!!
                )
            }

            val query = database.sqlQueryGenerator.createSelectQuery(
                tableName = from.getTableName(),
                selected = slice,
                joins = joins,
                where = expression?.query
            )

            val arguments = query.arguments + expression?.arguments
            database.logger.log(Level.DEBUG, "Executing query: ${query.query} with arguments: $arguments")

            handle
                .select(query.query)
                .bindArguments(arguments)
                .map { view -> mapper(Row(view)) }
                .list()
                .asSequence()
        }


}
