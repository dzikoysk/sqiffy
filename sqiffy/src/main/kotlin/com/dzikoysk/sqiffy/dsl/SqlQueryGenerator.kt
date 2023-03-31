package com.dzikoysk.sqiffy.dsl

import com.dzikoysk.sqiffy.dsl.select.Join
import com.dzikoysk.sqiffy.dsl.select.JoinType
import com.dzikoysk.sqiffy.shared.multiline
import com.dzikoysk.sqiffy.shared.toQuoted

typealias QueryString = String
typealias Argument = String
typealias Value = Any
typealias Arguments = Map<Argument, Value>

class ParameterAllocator {
    private val arguments = mutableListOf<Argument>()
    fun allocate(): Argument = "${arguments.size}".also { arguments.add(it) }
}

interface SqlQueryGenerator {

    /* Queries */

    fun createSelectQuery(
        tableName: String,
        selected: List<String>,
        where: String? = null,
        joins: List<Join> = emptyList()
    ): Pair<QueryString, Arguments>

    fun createInsertQuery(allocator: ParameterAllocator, tableName: String, columns: List<String>): Pair<QueryString, Arguments>

    fun createUpdateQuery(tableName: String, columns: List<String>, where: String? = null): Pair<QueryString, Arguments>

    fun createExpression(allocator: ParameterAllocator, expression: Expression<*>): Pair<QueryString, Arguments> =
        when (expression) {
            is Column -> expression.toQuotedIdentifier() to emptyMap()
            is ConstExpression -> allocator.allocate().let { argument ->
                ":$argument" to mapOf(argument to expression.value!!)
            }
            is EqualsExpression<*> -> {
                val leftResult = createExpression(allocator, expression.left)
                val rightResult = createExpression(allocator, expression.right)
                "${leftResult.first} = ${rightResult.first}" to (leftResult.second + rightResult.second)
            }
        }

}

object MySqlQueryGenerator : GenericQueryGenerator()

object PostgreSqlQueryGenerator : GenericQueryGenerator()

abstract class GenericQueryGenerator : SqlQueryGenerator {

    /* Queries */

    override fun createSelectQuery(
        tableName: String,
        selected: List<String>,
        where: String?,
        joins: List<Join>
    ): Pair<QueryString, Arguments> =
        multiline("""
            SELECT ${selected.joinToString(separator = ", ") { it.toQuoted() }}
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
        """) to emptyMap()

    override fun createInsertQuery(allocator: ParameterAllocator, tableName: String, columns: List<String>): Pair<QueryString, Arguments> {
        val arguments = mutableMapOf<String, String>()

        val values = columns.joinToString(
            separator = ", ",
            transform = {
                val argument = allocator.allocate()
                arguments[argument] = it
                ":$argument"
            }
        )

        return """INSERT INTO "$tableName" (${columns.joinToString(separator = ", ") { it.toQuoted() }}) VALUES ($values)""" to arguments
    }

    override fun createUpdateQuery(tableName: String, columns: List<String>, where: String?): Pair<QueryString, Arguments> =
        multiline("""
            UPDATE "$tableName"
            SET ${columns.joinToString(separator = ", ") { it.toQuoted() + " = :$it" }}
            ${where?.let { "WHERE $it" } ?: ""}
        """)  to emptyMap()

}