package com.dzikoysk.sqiffy.dsl.statements

import com.dzikoysk.sqiffy.SqiffyDatabase
import com.dzikoysk.sqiffy.dsl.Column
import com.dzikoysk.sqiffy.dsl.Expression
import com.dzikoysk.sqiffy.dsl.Statement
import com.dzikoysk.sqiffy.dsl.Table
import com.dzikoysk.sqiffy.dsl.generator.ParameterAllocator
import com.dzikoysk.sqiffy.dsl.generator.bindArguments
import com.dzikoysk.sqiffy.transaction.HandleAccessor
import org.slf4j.event.Level

open class DeleteStatement(
    protected val database: SqiffyDatabase,
    protected val handleAccessor: HandleAccessor,
    protected val table: Table,
) : Statement {

    protected var where: Expression<*, Boolean>? = null

    fun where(where: () -> Expression<out Column<*>, Boolean>): DeleteStatement = also {
        this.where = where()
    }

    fun execute(): Int {
        val allocator = ParameterAllocator()

        val expression = where?.let {
            database.sqlQueryGenerator.createExpression(
                allocator = allocator,
                expression = where!!
            )
        }

        val (query, queryArguments) = database.sqlQueryGenerator.createDeleteQuery(
            tableName = table.getName(),
            where = expression?.query
        )

        val arguments = queryArguments + expression?.arguments
        database.logger.log(Level.DEBUG, "Executing query: $query with arguments: $arguments")

        return handleAccessor.inHandle { handle ->
            handle
                .createUpdate(query)
                .bindArguments(arguments)
                .execute()
        }
    }

}