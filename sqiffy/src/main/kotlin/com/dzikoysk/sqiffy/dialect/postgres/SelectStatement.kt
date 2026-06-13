package com.dzikoysk.sqiffy.dialect.postgres

import com.dzikoysk.sqiffy.SqiffyDatabase
import com.dzikoysk.sqiffy.dsl.Selectable
import com.dzikoysk.sqiffy.dsl.Table
import com.dzikoysk.sqiffy.dsl.statements.Order
import com.dzikoysk.sqiffy.dsl.statements.OrderBy
import com.dzikoysk.sqiffy.dsl.statements.SelectStatement as BaseSelectStatement
import com.dzikoysk.sqiffy.transaction.HandleAccessor

/**
 * PostgreSQL-specific select statement that exposes ordering attributes (`NULLS FIRST` / `NULLS LAST`)
 * which are not portable across all supported dialects.
 */
class SelectStatement(
    database: SqiffyDatabase<*>,
    handleAccessor: HandleAccessor,
    from: Table,
) : BaseSelectStatement(database, handleAccessor, from) {

    fun orderBy(block: OrderByScope.() -> Unit): SelectStatement = also {
        require(this.orderBy == null) { "Order by clause is already defined" }
        this.orderBy = OrderByScope().apply(block).orders
    }

}

enum class NullsOrder {
    FIRST,
    LAST
}

class PostgresOrderBy internal constructor(
    selectable: Selectable,
    order: Order,
) : OrderBy(selectable, order) {

    var nullsOrder: NullsOrder? = null
        private set

    fun nullsFirst(): PostgresOrderBy = apply { nullsOrder = NullsOrder.FIRST }
    fun nullsLast(): PostgresOrderBy = apply { nullsOrder = NullsOrder.LAST }

}

@DslMarker
private annotation class OrderByDsl

/**
 * Receiver scope for [SelectStatement.orderBy]. The ordering builders are member extensions, so they are
 * only reachable inside the `orderBy { }` block of a PostgreSQL select and cannot be imported elsewhere.
 */
@OrderByDsl
class OrderByScope internal constructor() {

    internal val orders = mutableListOf<OrderBy>()

    fun Selectable.asc(): PostgresOrderBy = PostgresOrderBy(this, Order.ASC).also { orders += it }
    fun Selectable.desc(): PostgresOrderBy = PostgresOrderBy(this, Order.DESC).also { orders += it }

}
