package com.dzikoysk.sqiffy

import com.dzikoysk.sqiffy.shared.executeQuery
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.transactions.TransactionManager
import org.jetbrains.exposed.sql.transactions.transaction

typealias Version = String
typealias Query = String
typealias Queries = MutableList<Query>

data class ChangeLog(
    val changes: LinkedHashMap<Version, Queries>
) {

    fun runMigrations(database: Database) {
        transaction(database) {
            changes.forEach { (version, changes) ->
                changes.forEach { change ->
                    println(change)
                    println(TransactionManager.current().connection.executeQuery("$change;"))
                }
            }
        }
    }

}
