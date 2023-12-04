package com.dzikoysk.sqiffy.transaction

import org.jdbi.v3.core.Handle
import org.jdbi.v3.core.Jdbi

interface HandleAccessor {
    fun <R> inHandle(body: (Handle) -> R): R
}

class StandardHandleAccessor(private val jdbi: Jdbi) : HandleAccessor {
    override fun <R> inHandle(body: (Handle) -> R): R = jdbi.withHandle<R, Exception> { body.invoke(it) }
}