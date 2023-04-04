package com.dzikoysk.sqiffy.dsl

import com.dzikoysk.sqiffy.shared.get
import org.jdbi.v3.core.result.ResultBearing
import org.jdbi.v3.core.result.ResultIterable
import org.jdbi.v3.core.result.RowView

interface Statement<QUERY : ResultBearing> {

    fun <R> execute(mapper: (QUERY) -> ResultIterable<R>): Sequence<R>

    fun <R> map(mapper: (Row) -> R): Sequence<R> =
        execute {
            it.map { view ->
                mapper(Row(view))
            }
        }

}

//inline fun <reified R : Any> Statement<out ResultBearing>.mapTo(): Sequence<R> =
//    execute {
//        it.mapTo()
//    }

class Row(val view: RowView) {
    operator fun <T> get(column: Column<T>): T = view[column]
}
