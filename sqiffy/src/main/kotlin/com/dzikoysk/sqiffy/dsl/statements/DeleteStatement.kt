package com.dzikoysk.sqiffy.dsl.statements

import com.dzikoysk.sqiffy.SqiffyDatabase
import com.dzikoysk.sqiffy.dsl.Expression
import com.dzikoysk.sqiffy.dsl.Statement
import com.dzikoysk.sqiffy.dsl.Table
import com.dzikoysk.sqiffy.dsl.generator.ParameterAllocator

open class DeleteStatement(
    protected val database: SqiffyDatabase,
    protected val table: Table,
) : Statement {

    protected var where: Expression<Boolean>? = null

    fun where(where: () -> Expression<Boolean>): DeleteStatement = also {
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

            val query = database.sqlQueryGenerator.createDeleteQuery(
                tableName = table.getTableName(),
                where = expression?.query
            )

            handle
                .createUpdate(query.query)
                .also {
                    expression?.arguments?.forEach { (argument, value) ->
                        it.bindByType(argument, value, value::class.javaObjectType)
                    }
                }
                .execute()
        }
    }

}