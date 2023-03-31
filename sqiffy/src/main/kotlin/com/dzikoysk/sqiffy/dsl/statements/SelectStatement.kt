package com.dzikoysk.sqiffy.dsl.statements

import com.dzikoysk.sqiffy.SqiffyDatabase
import com.dzikoysk.sqiffy.dsl.Column
import com.dzikoysk.sqiffy.dsl.Expression
import com.dzikoysk.sqiffy.dsl.ParameterAllocator
import com.dzikoysk.sqiffy.dsl.Statement
import com.dzikoysk.sqiffy.dsl.Table
import org.jdbi.v3.core.result.ResultIterable
import org.jdbi.v3.core.statement.Query
import org.slf4j.event.Level

class SelectStatementBuilder(
    val database: SqiffyDatabase,
    val from: Table,
    val where: Expression<Boolean>? = null
) : Statement<Query> {

    private var slice: List<Column<*>> = from.getColumns()

    override fun <R> execute(mapper: (Query) -> ResultIterable<R>): Sequence<R> =
        database.getJdbi().withHandle<Sequence<R>, Exception> { handle ->
            val allocator = ParameterAllocator()

            val expression = where?.let {
                database.sqlQueryGenerator.createExpression(
                    allocator = allocator,
                    expression = where
                )
            }

            val query = database.sqlQueryGenerator.createSelectQuery(
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


}
