package com.dzikoysk.sqiffy.dsl.generator

import com.dzikoysk.sqiffy.dsl.Column
import com.dzikoysk.sqiffy.dsl.Expression
import com.dzikoysk.sqiffy.dsl.QuoteType
import com.dzikoysk.sqiffy.dsl.Selectable
import com.dzikoysk.sqiffy.dsl.statements.Join
import com.dzikoysk.sqiffy.dsl.statements.OrderBy

class QueryColumn(
    val table: String,
    val name: String,
    val dbType: String,
    val type: Class<*>
)

fun Column<*>.toQueryColumn(): QueryColumn =
    QueryColumn(
        table = table.getName(),
        name = name,
        dbType = dbType,
        type = type
    )

typealias ExpressionColumns = Map<QueryColumn, String>

interface SqlQueryGenerator {

    data class GeneratorResult(
        val query: String,
        val arguments: Arguments = Arguments(ParameterAllocator())
    )

    fun createSelectQuery(
        tableName: String,
        distinct: Boolean = false,
        selected: List<Selectable>,
        where: String? = null,
        joins: List<Join> = emptyList(),
        groupBy: List<Column<*>>? = null,
        having: String? = null,
        orderBy: List<OrderBy>? = null,
        limit: Int? = null,
        offset: Int? = null,
    ): GeneratorResult

    data class InsertGeneratorResult(
        /* standard insert query */
        val updateResult: GeneratorResult,
        /* (optional) for drivers that don't support RETURN_GENERATED_KEYS */
        val customSelectForAutogeneratedKey: String? = null
    )

    fun createInsertQuery(
        allocator: ParameterAllocator,
        tableName: String,
        columns: List<QueryColumn>,
        autogeneratedKey: QueryColumn?
    ): InsertGeneratorResult

    fun createUpdateQuery(
        allocator: ParameterAllocator,
        tableName: String,
        argumentColumns: List<QueryColumn>,
        expressionColumns: ExpressionColumns = emptyMap(),
        where: String? = null
    ): GeneratorResult

    fun createDeleteQuery(
        tableName: String,
        where: String? = null
    ): GeneratorResult

    /* Expressions */

    fun <SOURCE, RESULT> createExpression(
        allocator: ParameterAllocator,
        expression: Expression<SOURCE, RESULT>
    ): GeneratorResult

    fun quoteType(): QuoteType =
        QuoteType.DOUBLE_QUOTE

}

