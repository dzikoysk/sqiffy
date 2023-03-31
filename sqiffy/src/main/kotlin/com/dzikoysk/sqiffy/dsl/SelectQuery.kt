package com.dzikoysk.sqiffy.dsl

import com.dzikoysk.sqiffy.SqiffyDatabase
import com.dzikoysk.sqiffy.shared.get
import org.jdbi.v3.core.kotlin.mapTo
import org.jdbi.v3.core.result.ResultIterable
import org.jdbi.v3.core.result.RowView
import org.jdbi.v3.core.statement.Query
import org.slf4j.event.Level

class SelectBuilder(
    val database: SqiffyDatabase,
    val from: Table,
    val where: Expression<Boolean>? = null
) {

    private var slice: List<Column<*>> = from.getColumns()

    fun <R> execute(mapper: (Query) -> ResultIterable<R>): Sequence<R> =
        database.getJdbi().withHandle<Sequence<R>, Exception> { handle ->
            val allocator = ParameterAllocator()

            val expression = where?.let {
                database.sqlQueryGenerator.createExpression(
                    allocator = allocator,
                    expression = where
                )
            }

            val query = database.sqlQueryGenerator.createSelectQuery(
                allocator = allocator,
                tableName = from.getTableName(),
                selected = slice.map { it.name },
                where = expression?.first
            )

            val arguments = query.second + (expression?.second ?: emptyMap())
            database.logger.log(Level.DEBUG, "Executing query: ${query.first} with arguments: $arguments")

            handle
                .select(query.first)
                .also {
                    arguments.forEach { (key, value) ->
                        it.bindByType(key, value, value::class.javaObjectType)
                    }
                }
                .let { mapper(it) }
                .list()
                .asSequence()
        }

    fun <R> map(mapper: (Row) -> R): Sequence<R> =
        execute { query ->
            query.map { view ->
                mapper(
                    Row(view = view)
                )
            }
        }

    inline fun <reified R : Any> mapTo(): Sequence<R> =
        execute { query ->
            query.mapTo()
        }

}

class Row(val view: RowView) {
    operator fun <T> get(column: Column<T>): T {
        return view[column]
    }
}