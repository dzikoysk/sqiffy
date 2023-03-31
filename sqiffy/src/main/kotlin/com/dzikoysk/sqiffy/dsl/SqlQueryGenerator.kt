package com.dzikoysk.sqiffy.dsl

import com.dzikoysk.sqiffy.shared.multiline
import com.dzikoysk.sqiffy.shared.toQuoted

typealias QueryString = String
typealias Argument = String
typealias Value = Any
typealias Arguments = Map<Argument, Value>

class ParameterAllocator {
    private val arguments = mutableListOf<Argument>()
    fun allocate(): Argument = "arg${arguments.size}".also { arguments.add(it) }
}

interface SqlQueryGenerator {

    /* Queries */

    fun createSelectQuery(tableName: String, selected: List<String>, where: String? = null): Pair<QueryString, Arguments>

    fun createInsertQuery(tableName: String, columns: List<String>): Pair<QueryString, Arguments>

    fun createUpdateQuery(tableName: String, columns: List<String>, where: String? = null): Pair<QueryString, Arguments>

    fun createExpression(allocator: ParameterAllocator, expression: Expression<*>): Pair<QueryString, Arguments> =
        when (expression) {
            is Column -> expression.name.toQuoted() to emptyMap()
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

    override fun createSelectQuery(tableName: String, selected: List<String>, where: String?): Pair<QueryString, Arguments> =
        multiline("""
            SELECT ${selected.joinToString(separator = ", ") { it.toQuoted() }}
            FROM "$tableName"
            ${where?.let { "WHERE $it" } ?: ""}
        """) to emptyMap()

    override fun createInsertQuery(tableName: String, columns: List<String>): Pair<QueryString, Arguments> =
        multiline("""
            INSERT INTO "$tableName" (${columns.joinToString(separator = ", ") { it.toQuoted() }})
            VALUES (${columns.joinToString(separator = ", ") { ":$it" }})
        """)  to emptyMap()

    override fun createUpdateQuery(tableName: String, columns: List<String>, where: String?): Pair<QueryString, Arguments> =
        multiline("""
            UPDATE "$tableName"
            SET ${columns.joinToString(separator = ", ") { it.toQuoted() + " = :$it" }}
            ${where?.let { "WHERE $it" } ?: ""}
        """)  to emptyMap()

}