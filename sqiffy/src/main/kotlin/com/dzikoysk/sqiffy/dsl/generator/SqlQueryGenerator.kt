package com.dzikoysk.sqiffy.dsl.generator

import com.dzikoysk.sqiffy.dsl.Column
import com.dzikoysk.sqiffy.dsl.ConstExpression
import com.dzikoysk.sqiffy.dsl.EqualsExpression
import com.dzikoysk.sqiffy.dsl.Expression
import com.dzikoysk.sqiffy.dsl.generator.SqlQueryGenerator.GeneratorResult
import com.dzikoysk.sqiffy.dsl.statements.Join
import com.dzikoysk.sqiffy.dsl.statements.JoinType
import com.dzikoysk.sqiffy.shared.multiline
import com.dzikoysk.sqiffy.shared.toQuoted

typealias Argument = String
typealias Value = Any

class QueryColumn(
    val table: String,
    val name: String,
    val dbType: String,
    val type: Class<*>
)

interface SqlQueryGenerator {

    data class GeneratorResult(
        val query: String,
        val arguments: Map<Argument, Value> = emptyMap()
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
        val arguments = mutableMapOf<String, String>()

        val values = columns.joinToString(
            separator = ", ",
            transform = {
                val argument = allocator.allocate()
                arguments[argument] = it.name
                ":$argument"
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

}

object PostgreSqlQueryGenerator : GenericQueryGenerator() {

    override fun createInsertQuery(
        allocator: ParameterAllocator,
        tableName: String,
        columns: List<QueryColumn>
    ): GeneratorResult {
        val arguments = mutableMapOf<String, String>()

        val values = columns.joinToString(
            separator = ", ",
            transform = {
                val argument = allocator.allocate()
                arguments[argument] = it.name

                when {
                    it.type.isEnum -> "CAST(:$argument AS ${it.dbType})"
                    else -> ":$argument"
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

    override fun createUpdateQuery(
        tableName: String,
        columns: List<QueryColumn>,
        where: String?
    ): GeneratorResult =
        GeneratorResult(
            query = multiline("""
                UPDATE "$tableName"
                SET ${columns.joinToString(separator = ", ") { "${it.name.toQuoted()} = :$it" }}
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
            is ConstExpression -> allocator.allocate().let { argument ->
                GeneratorResult(
                    query = ":$argument",
                    arguments = mapOf(argument to expression.value!!)
                )
            }
            is EqualsExpression<*> -> {
                val leftResult = createExpression(allocator, expression.left)
                val rightResult = createExpression(allocator, expression.right)

                GeneratorResult(
                    query = "${leftResult.query} = ${rightResult.query}",
                    arguments = (leftResult.arguments + rightResult.arguments)
                )
            }
        }

}