package com.dzikoysk.sqiffy.dsl.generator

import com.dzikoysk.sqiffy.dsl.BetweenCondition
import com.dzikoysk.sqiffy.dsl.Column
import com.dzikoysk.sqiffy.dsl.ComparisonCondition
import com.dzikoysk.sqiffy.dsl.ConstExpression
import com.dzikoysk.sqiffy.dsl.Expression
import com.dzikoysk.sqiffy.dsl.LogicalCondition
import com.dzikoysk.sqiffy.dsl.NotCondition
import com.dzikoysk.sqiffy.dsl.generator.ArgumentType.COLUMN
import com.dzikoysk.sqiffy.dsl.generator.ArgumentType.VALUE
import com.dzikoysk.sqiffy.dsl.generator.SqlQueryGenerator.GeneratorResult
import com.dzikoysk.sqiffy.dsl.statements.Join
import com.dzikoysk.sqiffy.dsl.statements.JoinType
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
        table = table.getTableName(),
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
        selected: List<QueryColumn>,
        where: String? = null,
        joins: List<Join> = emptyList()
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
        expression: Expression<*>
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

    override fun createSelectQuery(
        tableName: String,
        selected: List<QueryColumn>,
        where: String?,
        joins: List<Join>
    ): GeneratorResult =
        GeneratorResult(
            query = multiline("""
                SELECT ${selected.joinToString(separator = ", ") { "${it.table.toQuoted()}.${it.name.toQuoted()} AS " + (it.table + "." + it.name).toQuoted() }}
                FROM ${tableName.toQuoted()}
                ${joins.joinToString(separator = " ") { join ->
                    val joinType = when (join.type) {
                        JoinType.INNER -> "INNER JOIN"
                        JoinType.LEFT -> "LEFT JOIN"
                        JoinType.RIGHT -> "RIGHT JOIN"
                        JoinType.FULL -> "FULL JOIN"
                    }
                    "$joinType ${join.onTo.table.getTableName().toQuoted()} ON ${join.on.toQuotedIdentifier()} = ${join.onTo.toQuotedIdentifier()}"
                }}
                ${where?.let { "WHERE $it" } ?: ""}
            """)
        )

    override fun createDeleteQuery(tableName: String, where: String?): GeneratorResult =
        GeneratorResult(
            query = multiline("""
                DELETE FROM "$tableName"
                ${where?.let { "WHERE $it" } ?: ""}
            """)
        )

    override fun createExpression(allocator: ParameterAllocator, expression: Expression<*>): GeneratorResult =
        when (expression) {
            is Column ->
                GeneratorResult(
                    query = expression.toQuotedIdentifier()
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
            is ComparisonCondition<*> -> {
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
            is BetweenCondition<*> -> {
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