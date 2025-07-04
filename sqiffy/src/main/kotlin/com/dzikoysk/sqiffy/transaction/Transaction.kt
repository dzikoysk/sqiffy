package com.dzikoysk.sqiffy.transaction

import com.dzikoysk.sqiffy.SqiffyDatabase
import com.dzikoysk.sqiffy.dsl.DslHandle
import com.dzikoysk.sqiffy.dsl.JdbiDslHandle
import org.jdbi.v3.core.Handle

sealed interface Transaction {
    companion object {
        operator fun Transaction.invoke(database: SqiffyDatabase): DslHandle = handle(database)
    }

    fun handle(database: SqiffyDatabase): DslHandle
}

data object NoTransaction : Transaction {
    override fun handle(database: SqiffyDatabase): DslHandle = database
}

data class JdbiTransaction(val handle: Handle) : Transaction {
    override fun handle(database: SqiffyDatabase): DslHandle = JdbiDslHandle(database, handle)
}

