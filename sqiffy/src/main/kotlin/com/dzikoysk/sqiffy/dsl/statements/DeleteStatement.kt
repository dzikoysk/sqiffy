package com.dzikoysk.sqiffy.dsl.statements

import com.dzikoysk.sqiffy.SqiffyDatabase
import com.dzikoysk.sqiffy.dsl.Column
import com.dzikoysk.sqiffy.dsl.Expression
import com.dzikoysk.sqiffy.dsl.Statement
import com.dzikoysk.sqiffy.dsl.Table
import com.dzikoysk.sqiffy.dsl.generator.ParameterAllocator
import com.dzikoysk.sqiffy.dsl.generator.bindArguments

open class DeleteStatement(
    protected val database: SqiffyDatabase,
    protected val table: Table,
) : Statement {

    protected var where: Expression<*, Boolean>? = null

    fun where(where: () -> Expression<out Column<*>, Boolean>): DeleteStatement = also {
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

            val (query, queryArguments) = database.sqlQueryGenerator.createDeleteQuery(
                tableName = table.getTableName(),
                where = expression?.query
            )

            val arguments = queryArguments + expression?.arguments

            handle
                .createUpdate(query)
                .bindArguments(arguments)
                .execute()
        }
    }

}