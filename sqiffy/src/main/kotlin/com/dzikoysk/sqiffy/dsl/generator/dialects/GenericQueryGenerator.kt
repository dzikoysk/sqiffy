package com.dzikoysk.sqiffy.dsl.generator.dialects

import com.dzikoysk.sqiffy.dsl.*
import com.dzikoysk.sqiffy.dsl.generator.ArgumentType.VALUE
import com.dzikoysk.sqiffy.dsl.generator.Arguments
import com.dzikoysk.sqiffy.dsl.generator.ExpressionColumns
import com.dzikoysk.sqiffy.dsl.generator.ParameterAllocator
import com.dzikoysk.sqiffy.dsl.generator.SqlQueryGenerator
import com.dzikoysk.sqiffy.dsl.generator.SqlQueryGenerator.GeneratorResult
import com.dzikoysk.sqiffy.dsl.statements.Join
import com.dzikoysk.sqiffy.dsl.statements.JoinType.FULL
import com.dzikoysk.sqiffy.dsl.statements.JoinType.INNER
import com.dzikoysk.sqiffy.dsl.statements.JoinType.LEFT
import com.dzikoysk.sqiffy.dsl.statements.JoinType.RIGHT
import com.dzikoysk.sqiffy.dsl.statements.OrderBy
import com.dzikoysk.sqiffy.shared.multiline
import kotlin.math.exp

abstract class GenericQueryGenerator : SqlQueryGenerator {

    protected open fun Selectable.toIdentifier(): String =
        when (this) {
            is Column<*> -> "${table.getName().toQuoted()}.${name.toQuoted()}"
            is Aggregation<*> -> "$aggregationFunction(${quotedIdentifier.toString(quoteType())})"
            else -> throw IllegalArgumentException("Unknown selectable type: $javaClass")
        }

    protected fun ExpressionColumns.toUpdateValues(): String =
        this
            .map { (column, expression) -> "${column.name.toQuoted()} = $expression" }
            .joinToString(separator = ", ")

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
                SELECT ${if (distinct) "DISTINCT" else ""} ${
                selected.joinToString(separator = ", ") {
                    it.toIdentifier() + " AS " + when (it) {
                        is Column<*> -> "${it.table.getName()}.${it.name}".toQuoted()
                        is Aggregation<*> -> ("${it.aggregationFunction}(${it.rawIdentifier})").toQuoted()
                        else -> throw IllegalArgumentException("Unknown selectable type: $javaClass")
                    }
                }
            }
                FROM ${tableName.toQuoted()}
                ${
                joins.joinToString(separator = " ") { join ->
                    val joinType = when (join.type) {
                        INNER -> "INNER JOIN"
                        LEFT -> "LEFT JOIN"
                        RIGHT -> "RIGHT JOIN"
                        FULL -> "FULL JOIN"
                    }
                    "$joinType ${
                        join.onTo.table.getName().toQuoted()
                    } ON ${join.on.quotedIdentifier.toString(quoteType())} = ${join.onTo.quotedIdentifier.toString(quoteType())}"
                }
            }
                ${where?.let { "WHERE $it" } ?: ""}
                ${groupBy?.let { "GROUP BY ${groupBy.joinToString(separator = ", ") { it.quotedIdentifier.toString(quoteType()) }}" } ?: ""}
                ${having?.let { "HAVING $it" } ?: ""}
                ${orderBy?.let { "ORDER BY ${orderBy.joinToString(separator = ", ") { "${it.selectable.toIdentifier()} ${it.order}" }}" } ?: ""}
                ${limit?.let { "LIMIT $it" } ?: ""}
                ${offset?.let { "OFFSET $it" } ?: ""} 
            """)
        )

    override fun createDeleteQuery(tableName: String, where: String?): GeneratorResult =
        GeneratorResult(
            query = multiline("""
                DELETE FROM ${tableName.toQuoted()}
                ${where?.let { "WHERE $it" } ?: ""}
            """)
        )

    override fun <SOURCE, RESULT> createExpression(allocator: ParameterAllocator, expression: Expression<SOURCE, RESULT>): GeneratorResult =
        when (expression) {
            is Column<*> ->
                GeneratorResult(
                    query = expression.quotedIdentifier.toString(quoteType())
                )
            is Aggregation<*> ->
                GeneratorResult(
                    query = "${expression.aggregationFunction}(${expression.quotedIdentifier.toString(quoteType())})"
                )
            is ConstExpression<*> ->
                Arguments(allocator).let {
                    GeneratorResult(
                        query = ":${it.createArgument(VALUE, expression.value!!)}",
                        arguments = it
                    )
                }
            is MathExpression<*, *> -> {
                val leftResult = createExpression(allocator, expression.left)
                val rightResult = createExpression(allocator, expression.right)

                GeneratorResult(
                    query = "${leftResult.query} ${expression.operator.symbol} ${rightResult.query}",
                    arguments = (leftResult.arguments + rightResult.arguments)
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
            is WithinCondition<*, *> -> {
                when {
                    expression.values.isEmpty() ->
                        when (expression.type) {
                            WithinType.IN -> GeneratorResult(query = "FALSE")
                            WithinType.NOT_IN -> GeneratorResult(query = "TRUE")
                        }
                    expression.values.size == 1 ->
                        createExpression(
                            allocator,
                            @Suppress("UNCHECKED_CAST")
                            ComparisonCondition<SOURCE, RESULT>(
                                operator = when (expression.type) {
                                    WithinType.IN -> ComparisonOperator.EQUALS
                                    WithinType.NOT_IN -> ComparisonOperator.NOT_EQUALS
                                },
                                left = expression.value as Expression<SOURCE, RESULT>,
                                right = expression.values.first() as Expression<SOURCE, RESULT>,
                            )
                        )
                    else -> {
                        val valueResult = createExpression(allocator, expression.value)
                        val inResults = expression.values.map { createExpression(allocator, it) }
                        val sql = when (expression.type) {
                            WithinType.IN -> "IN"
                            WithinType.NOT_IN -> "NOT IN"
                        }

                        GeneratorResult(
                            query = "${valueResult.query} $sql (${inResults.joinToString(separator = ", ") { it.query }})",
                            arguments = (inResults.fold(Arguments(allocator)) { arguments, result -> arguments + result.arguments } + valueResult.arguments)
                        )
                    }
                }
            }
        }

    fun String.toQuoted() =
        quoteType().quote(this)

}