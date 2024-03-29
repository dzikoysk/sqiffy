package com.dzikoysk.sqiffy.dialect.postgres

import com.dzikoysk.sqiffy.SqiffyDatabase
import com.dzikoysk.sqiffy.definition.DataType.SERIAL
import com.dzikoysk.sqiffy.dsl.Expression
import com.dzikoysk.sqiffy.dsl.Row
import com.dzikoysk.sqiffy.dsl.Statement
import com.dzikoysk.sqiffy.dsl.TableWithAutogeneratedKey
import com.dzikoysk.sqiffy.dsl.Values
import com.dzikoysk.sqiffy.dsl.generator.Arguments
import com.dzikoysk.sqiffy.dsl.generator.ParameterAllocator
import com.dzikoysk.sqiffy.dsl.generator.bindArguments
import com.dzikoysk.sqiffy.dsl.generator.dialects.PostgreSqlQueryGenerator
import com.dzikoysk.sqiffy.dsl.generator.toQueryColumn
import com.dzikoysk.sqiffy.dsl.statements.UpdateValues
import com.dzikoysk.sqiffy.transaction.HandleAccessor
import org.slf4j.event.Level

typealias InsertValuesBody = (Values) -> Unit
typealias UpdateValuesBody = (UpdateValues) -> Unit

open class UpsertStatement<KEY>(
    protected val database: SqiffyDatabase,
    protected val handleAccessor: HandleAccessor,
    protected val table: TableWithAutogeneratedKey<KEY>,
    protected var insertValuesSupplier: InsertValuesBody?,
    protected var updateValuesSupplier: UpdateValuesBody?
) : Statement {

    protected val autogeneratedKey = table
        .getColumns()
        .first { it.dataType == SERIAL }

    protected var where: Expression<*, Boolean>? = null

    fun insert(insertValues: InsertValuesBody): UpsertStatement<KEY> = also {
        this.insertValuesSupplier = insertValues
    }

    fun update(updateValues: UpdateValuesBody): UpsertStatement<KEY> = also {
        this.updateValuesSupplier = updateValues
    }

//    fun where(where: () -> Expression<out Column<*>, Boolean>): UpsertStatement<KEY> = also {
//        this.where = where()
//    }

    fun <T> execute(mapper: (Row) -> T): List<T> {
        val insertValues = Values().also { insertValuesSupplier?.invoke(it) ?: throw IllegalStateException("Insert values are not defined") }
        val updateValues = UpdateValues().also { updateValuesSupplier?.invoke(it) ?: throw IllegalStateException("Update values are not defined") }

        val allocator = ParameterAllocator()

        val upsertExpressions = updateValues.updateToExpressions
            .map { (column, expression) ->
                val expressionQuery = database.sqlQueryGenerator.createExpression(
                    allocator = allocator,
                    expression = expression
                )

                column to expressionQuery
            }
            .toMap()

        val whereExpression = where?.let {
            database.sqlQueryGenerator.createExpression(
                allocator = allocator,
                expression = where!!
            )
        }

        val upsertResult = PostgreSqlQueryGenerator.createUpsertQuery(
            allocator = allocator,
            tableName = table.getName(),
            insertColumns = insertValues.getColumns().map { it.toQueryColumn() },
            updateColumns = updateValues.getColumns().map { it.toQueryColumn() },
            expressionColumns = upsertExpressions.mapKeys { it.key.toQueryColumn() }.mapValues { it.value.query },
            autogeneratedKey = autogeneratedKey.toQueryColumn(),
            where = whereExpression?.query
        )

        database.logger.log(Level.DEBUG, "Executing query: ${upsertResult.query}")

        return handleAccessor.inHandle { handle ->
            handle
                .createUpdate(upsertResult.query)
                .bindArguments(upsertResult.insertArguments, insertValues)
                .bindArguments(upsertResult.updateArguments + upsertExpressions.values.map { it.arguments }, updateValues)
                .bindArguments(whereExpression?.arguments ?: Arguments(allocator))
                .executeAndReturnGeneratedKeys()
                .map { view -> mapper(Row(view)) }
                .list()
        }
    }

}