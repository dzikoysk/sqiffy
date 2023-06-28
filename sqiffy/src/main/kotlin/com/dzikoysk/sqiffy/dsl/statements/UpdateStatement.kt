package com.dzikoysk.sqiffy.dsl.statements

import com.dzikoysk.sqiffy.SqiffyDatabase
import com.dzikoysk.sqiffy.dsl.Column
import com.dzikoysk.sqiffy.dsl.Expression
import com.dzikoysk.sqiffy.dsl.Statement
import com.dzikoysk.sqiffy.dsl.Table
import com.dzikoysk.sqiffy.dsl.Values
import com.dzikoysk.sqiffy.dsl.generator.ParameterAllocator
import com.dzikoysk.sqiffy.dsl.generator.bindArguments
import com.dzikoysk.sqiffy.dsl.generator.toQueryColumn
import org.slf4j.event.Level

class UpdateValues : Values() {

    internal val updateToExpressions: MutableMap<Column<*>, Expression<*, *>> = mutableMapOf()

    operator fun <T : Any?> set(column: Column<T>, value: Expression<*, T>) {
        updateToExpressions[column] = value
    }

}

open class UpdateStatement(
    protected val database: SqiffyDatabase,
    protected val table: Table,
    protected val values: UpdateValues
) : Statement {

    protected var where: Expression<*, Boolean>? = null

    fun where(where: () -> Expression<out Column<*>, Boolean>): UpdateStatement = also {
        this.where = where()
    }

    fun execute(): Int {
        return database.getJdbi().withHandle<Int, Exception> { handle ->
            val allocator = ParameterAllocator()

            val whereExpression = where?.let {
                database.sqlQueryGenerator.createExpression(
                    allocator = allocator,
                    expression = where!!
                )
            }

            val updateExpressions = values.updateToExpressions
                .map { (column, expression) ->
                    val expressionQuery = database.sqlQueryGenerator.createExpression(
                        allocator = allocator,
                        expression = expression
                    )

                    column to expressionQuery
                }
                .toMap()

            val (query, queryArguments) = database.sqlQueryGenerator.createUpdateQuery(
                allocator = allocator,
                tableName = table.getName(),
                argumentColumns = values.getColumns().map { it.toQueryColumn() },
                expressionColumns = updateExpressions.mapKeys { it.key.toQueryColumn() }.mapValues { it.value.query },
                where = whereExpression?.query,
            )

            val arguments = queryArguments + whereExpression?.arguments + updateExpressions.values.map { it.arguments }
            database.logger.log(Level.DEBUG, "Executing query: $query with arguments: $arguments")

            handle
                .createUpdate(query)
                .bindArguments(arguments, values)
                .execute()
        }
    }

}