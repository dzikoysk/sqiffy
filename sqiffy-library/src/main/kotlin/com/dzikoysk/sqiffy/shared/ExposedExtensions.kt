package com.dzikoysk.sqiffy.shared

import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.Op
import org.jetbrains.exposed.sql.SqlExpressionBuilder
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.statements.api.ExposedConnection
import org.jetbrains.exposed.sql.statements.api.PreparedStatementApi
import org.jetbrains.exposed.sql.transactions.TransactionManager
import org.jetbrains.exposed.sql.transactions.transaction
import java.io.File
import java.nio.file.Path

fun LinkedHashMap<String, MutableList<String>>.runMigrations(database: Database) {
    transaction(database) {
        forEach { (version, changes) ->
            changes.forEach { change ->
                println(change)
                println(TransactionManager.current().connection.executeQuery("$change;"))
            }
        }
    }
}

fun ExposedConnection<*>.executeQuery(
    query: String,
    returnKeys: Boolean = false
): Int {
    var statement: PreparedStatementApi? = null

    try {
        statement = prepareStatement(query, returnKeys)
        return statement.executeUpdate()
    } finally {
        statement?.closeIfPossible()
    }
}

fun Op.Companion.andOf(vararg ops: SqlExpressionBuilder.() -> Op<Boolean>): Op<Boolean> {
    require(ops.isNotEmpty()) { "At least one operation required to build 'and' query" }
    var operation: Op<Boolean>? = null
    ops.forEach { operation = operation?.and(it) ?: build(it) }
    return operation!!
}

fun createTestDatabaseFile(name: String): Path =
    File.createTempFile(name, ".db")
        .also { it.deleteOnExit() }
        .toPath()