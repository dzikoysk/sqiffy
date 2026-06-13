package com.dzikoysk.sqiffy.transaction

import com.dzikoysk.sqiffy.SqiffyDatabase
import com.dzikoysk.sqiffy.dsl.DslHandle
import org.jdbi.v3.core.Handle

sealed interface Transaction {
    companion object {
        operator fun Transaction.invoke(database: SqiffyDatabase<*>): DslHandle<*> = database.with(this)
    }
}

data object NoTransaction : Transaction

data class JdbiTransaction(val handle: Handle) : Transaction
