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

open class UpdateStatement(
    protected val database: SqiffyDatabase,
    protected val table: Table,
    protected val values: Values
) : Statement {

    protected var where: Expression<*, Boolean>? = null

    fun where(where: () -> Expression<out Column<*>, Boolean>): UpdateStatement = also {
        this.where = where()
    }

    fun execute(): Int {
        return database.getJdbi().withHandle<Int, Exception> { handle ->
            val allocator = ParameterAllocator()

            val expression = where?.let {
                database.sqlQueryGenerator.createExpression(
                    allocator = allocator,
                    expression = where!!
                )
            }

            val (query, queryArguments) = database.sqlQueryGenerator.createUpdateQuery(
                allocator = allocator,
                tableName = table.getName(),
                columns = values.getColumns().map { it.toQueryColumn() },
                where = expression?.query
            )

            val arguments = queryArguments + expression?.arguments
            database.logger.log(Level.DEBUG, "Executing query: $query with arguments: $arguments")

            handle
                .createUpdate(query)
                .bindArguments(arguments, values)
                .execute()
        }
    }

}