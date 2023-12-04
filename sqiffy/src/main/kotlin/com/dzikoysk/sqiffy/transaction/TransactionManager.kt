package com.dzikoysk.sqiffy.transaction

interface TransactionManager {
    fun <T> transaction(block: (Transaction) -> T): T
}

object NoTransactionManager : TransactionManager {
    override fun <T> transaction(block: (Transaction) -> T): T = block.invoke(NoTransaction)
}
