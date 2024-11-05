@file:Suppress("MemberVisibilityCanBePrivate", "unused")

package com.dzikoysk.sqiffy.dsl.statements

import com.dzikoysk.sqiffy.SqiffyDatabase
import com.dzikoysk.sqiffy.dsl.Aggregation
import com.dzikoysk.sqiffy.dsl.Column
import com.dzikoysk.sqiffy.dsl.Expression
import com.dzikoysk.sqiffy.transaction.HandleAccessor
import com.dzikoysk.sqiffy.dsl.Row
import com.dzikoysk.sqiffy.dsl.RowException
import com.dzikoysk.sqiffy.dsl.Selectable
import com.dzikoysk.sqiffy.dsl.Statement
import com.dzikoysk.sqiffy.dsl.Table
import com.dzikoysk.sqiffy.dsl.generator.ParameterAllocator
import com.dzikoysk.sqiffy.dsl.generator.bindArguments
import org.slf4j.event.Level

enum class JoinType {
    INNER,
    LEFT,
    RIGHT,
    FULL
}

data class Join(
    val type: JoinType,
    val table: Table,
    val conditions: List<JoinCondition>
)

data class JoinCondition(
    val on: Column<*>,
    val toExpression: Expression<*, *>
)

enum class Order {
    ASC,
    DESC
}

data class OrderBy(
    val selectable: Selectable,
    val order: Order
)

open class SelectStatement(
    protected val database: SqiffyDatabase,
    protected val handleAccessor: HandleAccessor,
    protected val from: Table,
) : Statement {

    protected var distinct: Boolean = false
    protected var slice: MutableList<Selectable> = from.getColumns().toMutableList()
    protected val joins: MutableList<Join> = mutableListOf()
    protected var where: Expression<*, Boolean>? = null
    protected var groupBy: List<Column<*>>? = null
    protected var having: Expression<*, Boolean>? = null
    protected var limit: Int? = null
    protected var offset: Int? = null
    protected var orderBy: List<OrderBy>? = null

    fun distinct(): SelectStatement = also {
        this.distinct = true
    }

    fun <T> leftJoin(on: Column<T>, to: Column<T>): SelectStatement =
        join(type = JoinType.LEFT, on = on, to = to)

    fun <T> innerJoin(on: Column<T>, to: Column<T>): SelectStatement =
        join(type = JoinType.INNER, on = on, to = to)

    fun <T> rightJoin(on: Column<T>, to: Column<T>): SelectStatement =
        join(type = JoinType.RIGHT, on = on, to = to)

    fun <T> join(type: JoinType, on: Column<T>, to: Column<T>): SelectStatement = also {
        val join = Join(type, to.table, listOf(JoinCondition(on = on, toExpression = to)))
        require(!joins.contains(join)) { "Join $join is already defined" }
        joins.add(join)
        slice.addAll(to.table.getColumns())
    }

    @ExperimentalStdlibApi
    fun <T : Table> join(type: JoinType, table: T, vararg conditions: (T) -> Pair<Column<*>, Expression<*, *>>): SelectStatement = also {
        val mappedConditions = conditions.map { it(table) }
        val joinedToColumns = mappedConditions.map { it.second }.filterIsInstance<Column<*>>()
        require(joinedToColumns.map { it.table }.toSet().size == 1) { "All joined columns must be from the same table" }

        val join = Join(type, table, mappedConditions.map { JoinCondition(on = it.first, toExpression = it.second) })
        require(!joins.contains(join)) { "Join $join is already defined" }
        joins.add(join)
        slice.addAll(joinedToColumns.flatMap { it.table.getColumns() })
    }

    fun slice(vararg selectables: Selectable): SelectStatement = also {
        this.slice = selectables.toMutableList()
    }

    fun slice(selectables: Collection<Selectable>): SelectStatement = also {
        this.slice = selectables.toMutableList()
    }

    fun where(where: () -> Expression<out Column<*>, Boolean>): SelectStatement = also {
        require(this.where == null) { "Where clause is already defined" }
        this.where = where()
    }

    fun groupBy(vararg columns: Column<*>): SelectStatement = also {
        require(this.groupBy == null) { "Group by clause is already defined" }
        this.groupBy = columns.toList()
    }

    fun groupBy(columns: Collection<Column<*>>): SelectStatement = also {
        require(this.groupBy == null) { "Group by clause is already defined" }
        this.groupBy = columns.toList()
    }

    fun having(having: () -> Expression<out Aggregation<*>, Boolean>): SelectStatement = also {
        require(this.having == null) { "Having clause is already defined" }
        this.having = having()
    }

    fun limit(limit: Int, offset: Int? = null): SelectStatement = also {
        this.limit = limit
        this.offset = offset
    }

    fun orderBy(vararg columns: Pair<Selectable, Order>): SelectStatement = also {
        require(this.orderBy == null) { "Order by clause is already defined" }
        this.orderBy = columns.map { OrderBy(it.first, it.second) }
    }

    fun <R> map(mapper: (Row) -> R): Sequence<R> {
        groupBy?.also { groupByColumns ->
            slice
                .filterIsInstance<Column<*>>()
                .filter { !groupByColumns.contains(it) }
                .takeIf { it.isNotEmpty() }
                ?.let { nonAggregatedColumns ->
                    database.logger.log(
                        Level.WARN,
                        "Non-aggregated columns: $nonAggregatedColumns used with group by clause"
                    )
                }
        }

        val allocator = ParameterAllocator()

        val whereResult = where?.let {
            database.sqlQueryGenerator.createExpression(
                allocator = allocator,
                expression = it
            )
        }

        val havingResult = having?.let {
            database.sqlQueryGenerator.createExpression(
                allocator = allocator,
                expression = it
            )
        }

        val joinResult = joins.flatMap { join ->
            join.conditions.map { condition ->
                val onExpressionResult = database.sqlQueryGenerator.createExpression(
                    allocator = allocator,
                    expression = condition.toExpression
                )
                condition to onExpressionResult
            }
        }

        val query = database.sqlQueryGenerator.createSelectQuery(
            tableName = from.getName(),
            distinct = distinct,
            selected = slice,
            joins = joins,
            joinsExpressions = joinResult.map { it.first.toExpression to it.second.query }.associate { it },
            where = whereResult?.query,
            groupBy = groupBy,
            having = havingResult?.query,
            orderBy = orderBy,
            limit = limit,
            offset = offset,
        )

        val arguments = query.arguments + whereResult?.arguments + havingResult?.arguments + joinResult.map { it.second.arguments }

        database.logger.log(Level.DEBUG, "Executing query: ${query.query} with arguments: $arguments")

        return handleAccessor.inHandle { handle ->
            handle
                .select(query.query)
                .bindArguments(arguments)
                .map { view ->
                    try {
                        mapper(Row(view))
                    } catch (rowException: RowException) {
                        throw when {
                            slice.contains(rowException.selectable) -> rowException
                            else -> IllegalStateException("Row mapper tried to access ${rowException.selectable.id} that is not defined in slice")
                        }
                    }
                }
                .list()
                .asSequence()
        }
    }

    fun <R> toList(mapper: (Row) -> R): List<R> =
        map(mapper).toList()

    fun <R> toSet(mapper: (Row) -> R): Set<R> =
        map(mapper).toSet()

}
