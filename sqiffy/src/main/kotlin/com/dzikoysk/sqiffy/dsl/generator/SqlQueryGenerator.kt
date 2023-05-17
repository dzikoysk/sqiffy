package com.dzikoysk.sqiffy.dsl.generator

import com.dzikoysk.sqiffy.dsl.Aggregation
import com.dzikoysk.sqiffy.dsl.BetweenCondition
import com.dzikoysk.sqiffy.dsl.Column
import com.dzikoysk.sqiffy.dsl.ComparisonCondition
import com.dzikoysk.sqiffy.dsl.ConstExpression
import com.dzikoysk.sqiffy.dsl.Expression
import com.dzikoysk.sqiffy.dsl.LogicalCondition
import com.dzikoysk.sqiffy.dsl.NotCondition
import com.dzikoysk.sqiffy.dsl.Selectable
import com.dzikoysk.sqiffy.dsl.generator.ArgumentType.COLUMN
import com.dzikoysk.sqiffy.dsl.generator.ArgumentType.VALUE
import com.dzikoysk.sqiffy.dsl.generator.SqlQueryGenerator.GeneratorResult
import com.dzikoysk.sqiffy.dsl.statements.Join
import com.dzikoysk.sqiffy.dsl.statements.JoinType
import com.dzikoysk.sqiffy.dsl.statements.OrderBy
import com.dzikoysk.sqiffy.shared.multiline
import com.dzikoysk.sqiffy.shared.toQuoted

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

    fun createInsertQuery(
        allocator: ParameterAllocator,
        tableName: String,
        columns: List<QueryColumn>
    ): GeneratorResult

    fun createUpdateQuery(
        allocator: ParameterAllocator,
        tableName: String,
        columns: List<QueryColumn>,
        where: String? = null
    ): GeneratorResult

    fun createDeleteQuery(
        tableName: String,
        where: String? = null
    ): GeneratorResult

    /* Expressions */

    fun createExpression(
        allocator: ParameterAllocator,
        expression: Expression<*, *>
    ): GeneratorResult

}

object MySqlQueryGenerator : GenericQueryGenerator() {

    override fun createInsertQuery(
        allocator: ParameterAllocator,
        tableName: String,
        columns: List<QueryColumn>
    ): GeneratorResult {
        val arguments = Arguments(allocator)

        val values = columns.joinToString(
            separator = ", ",
            transform = { ":${arguments.createArgument(COLUMN, it.name)}" }
        )

        return GeneratorResult(
            query =
                multiline("""
                    INSERT INTO "$tableName" 
                    (${columns.joinToString(separator = ", ") { it.name.toQuoted() }})
                     VALUES ($values)
                """),
            arguments = arguments
        )
    }

    override fun createUpdateQuery(
        allocator: ParameterAllocator,
        tableName: String,
        columns: List<QueryColumn>,
        where: String?
    ): GeneratorResult {
        val arguments = Arguments(allocator)

        val values = columns.joinToString(
            separator = ", ",
            transform = { "${it.name.toQuoted()} = :${arguments.createArgument(COLUMN, it.name)}" }
        )

        return GeneratorResult(
            query =
                multiline("""
                    UPDATE "$tableName" 
                    SET $values
                    ${where?.let { "WHERE $it" } ?: ""}
                """),
            arguments = arguments
        )
    }

}

object PostgreSqlQueryGenerator : GenericQueryGenerator() {

    override fun createInsertQuery(
        allocator: ParameterAllocator,
        tableName: String,
        columns: List<QueryColumn>
    ): GeneratorResult {
        val arguments = Arguments(allocator)

        val values = columns.joinToString(
            separator = ", ",
            transform = {
                when {
                    it.type.isEnum -> "CAST(:${arguments.createArgument(COLUMN, it.name)} AS ${it.dbType})"
                    else -> ":${arguments.createArgument(COLUMN, it.name)}"
                }
            }
        )

        return GeneratorResult(
            query =
                multiline("""
                    INSERT INTO "$tableName" 
                    (${columns.joinToString(separator = ", ") { it.name.toQuoted() }}) 
                    VALUES ($values)
                """),
            arguments = arguments
        )
    }

    override fun createUpdateQuery(
        allocator: ParameterAllocator,
        tableName: String,
        columns: List<QueryColumn>,
        where: String?
    ): GeneratorResult {
        val arguments = Arguments(allocator)

        val values = columns.joinToString(
            separator = ", ",
            transform = {
                it.name.toQuoted() + " = " + when {
                    it.type.isEnum -> "CAST(:${arguments.createArgument(COLUMN, it.name)} AS ${it.dbType})"
                    else -> ":${arguments.createArgument(COLUMN, it.name)}"
                }
            }
        )

        return GeneratorResult(
            query =
            multiline("""
                    UPDATE "$tableName" 
                    SET $values
                    ${where?.let { "WHERE $it" } ?: ""}
                """),
            arguments = arguments
        )
    }

}

abstract class GenericQueryGenerator : SqlQueryGenerator {

    private fun Selectable.toIdentifier(): String =
        when (this) {
            is Column<*> -> quotedIdentifier
            is Aggregation<*> -> "$aggregationFunction($quotedIdentifier)"
            else -> throw IllegalArgumentException("Unknown selectable type: $javaClass")
        }

    override fun createSelectQuery(
        tableName: String,
        distinct: Boolean,
        selected: List<Selectable>,
        where: String?,
        joins: List<Join>,
        groupBy: List<Column<*>>?,
        having: String?,
        orderBy: List<OrderBy>?,
        limit: Int?,
        offset: Int?,
    ): GeneratorResult =
        GeneratorResult(
            query = multiline("""
                SELECT ${if (distinct) "DISTINCT" else ""} ${selected.joinToString(separator = ", ") { 
                    it.toIdentifier() + " AS " + when (it) {
                        is Column<*> -> "${it.table.getName()}.${it.name}".toQuoted()
                        is Aggregation<*> -> ("${it.aggregationFunction}(${it.rawIdentifier})").toQuoted()
                        else -> throw IllegalArgumentException("Unknown selectable type: $javaClass")
                    }
                }}
                FROM ${tableName.toQuoted()}
                ${joins.joinToString(separator = " ") { join ->
                    val joinType = when (join.type) {
                        JoinType.INNER -> "INNER JOIN"
                        JoinType.LEFT -> "LEFT JOIN"
                        JoinType.RIGHT -> "RIGHT JOIN"
                        JoinType.FULL -> "FULL JOIN"
                    }
                    "$joinType ${join.onTo.table.getName().toQuoted()} ON ${join.on.quotedIdentifier} = ${join.onTo.quotedIdentifier}"
                }}
                ${where?.let { "WHERE $it" } ?: ""}
                ${groupBy?.let { "GROUP BY ${groupBy.joinToString(separator = ", ") { it.quotedIdentifier }}" } ?: ""}
                ${having?.let { "HAVING $it" } ?: ""}
                ${orderBy?.let { "ORDER BY ${orderBy.joinToString(separator = ", ") { "${it.selectable.toIdentifier()} ${it.order}" }}" } ?: ""}
                ${limit?.let { "LIMIT $it" } ?: ""}
                ${offset?.let { "OFFSET $it" } ?: ""} 
            """)
        )

    override fun createDeleteQuery(tableName: String, where: String?): GeneratorResult =
        GeneratorResult(
            query = multiline("""
                DELETE FROM "$tableName"
                ${where?.let { "WHERE $it" } ?: ""}
            """)
        )

    override fun createExpression(allocator: ParameterAllocator, expression: Expression<*, *>): GeneratorResult =
        when (expression) {
            is Column<*> ->
                GeneratorResult(
                    query = expression.quotedIdentifier
                )
            is Aggregation<*> ->
                GeneratorResult(
                    query = "${expression.aggregationFunction}(${expression.quotedIdentifier})"
                )
            is ConstExpression ->
                Arguments(allocator).let {
                    GeneratorResult(
                        query = ":${it.createArgument(VALUE, expression.value!!)}",
                        arguments = it
                    )
                }
            is NotCondition -> {
                createExpression(allocator, expression.condition).let {
                    GeneratorResult(
                        query = "NOT ${it.query}",
                        arguments = it.arguments
                    )
                }
            }
            is ComparisonCondition<*, *> -> {
                val leftResult = createExpression(allocator, expression.left)
                val rightResult = createExpression(allocator, expression.right)

                GeneratorResult(
                    query = "${leftResult.query} ${expression.operator.symbol} ${rightResult.query}",
                    arguments = (leftResult.arguments + rightResult.arguments)
                )
            }
            is LogicalCondition -> {
                val results = expression.conditions.map { createExpression(allocator, it) }

                GeneratorResult(
                    query = results.joinToString(separator = " ${expression.operator.symbol} ") { "(${it.query})" },
                    arguments = results.fold(Arguments(allocator)) { arguments, result -> arguments + result.arguments }
                )
            }
            is BetweenCondition<*, *> -> {
                val valueResult = createExpression(allocator, expression.value)
                val leftResult = createExpression(allocator, expression.between.from)
                val rightResult = createExpression(allocator, expression.between.to)

                GeneratorResult(
                    query = "${valueResult.query} BETWEEN ${leftResult.query} AND ${rightResult.query}",
                    arguments = (valueResult.arguments + leftResult.arguments + rightResult.arguments)
                )
            }
        }

}