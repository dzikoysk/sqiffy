package com.dzikoysk.sqiffy.transaction

import com.dzikoysk.sqiffy.SqiffyDatabase
import com.dzikoysk.sqiffy.dsl.DslHandle
import com.dzikoysk.sqiffy.dsl.JdbiDslHandle
import org.jdbi.v3.core.Handle

sealed interface Transaction {
    operator fun invoke(database: SqiffyDatabase): DslHandle
}

data object NoTransaction : Transaction {
    override fun invoke(database: SqiffyDatabase): DslHandle = database
}

data class JdbiTransaction(val handle: Handle) : Transaction {
    override fun invoke(database: SqiffyDatabase): DslHandle = JdbiDslHandle(database, handle)
}

