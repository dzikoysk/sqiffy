package com.dzikoysk.sqiffy.dsl.statements

import com.dzikoysk.sqiffy.SqiffyDatabase
import com.dzikoysk.sqiffy.dsl.Row
import com.dzikoysk.sqiffy.dsl.Statement
import com.dzikoysk.sqiffy.dsl.Table
import com.dzikoysk.sqiffy.dsl.Values
import com.dzikoysk.sqiffy.dsl.generator.ParameterAllocator
import com.dzikoysk.sqiffy.dsl.generator.bindArguments
import com.dzikoysk.sqiffy.dsl.generator.toQueryColumn
import org.slf4j.event.Level

open class InsertStatement(
    protected val database: SqiffyDatabase,
    protected val table: Table,
    protected val values: Values
) : Statement {

    fun <T> map(mapper: (Row) -> T): Sequence<T> {
        return database.getJdbi().withHandle<Sequence<T>, Exception> { handle ->
            val allocator = ParameterAllocator()

            val (query, args) = database.sqlQueryGenerator.createInsertQuery(
                allocator = allocator,
                tableName = table.getTableName(),
                columns = values.getColumns().map { it.toQueryColumn() }
            )

            database.logger.log(Level.DEBUG, "Executing query: $query with arguments: $args")

            handle
                .createUpdate(query)
                .bindArguments(args, values)
                .executeAndReturnGeneratedKeys()
                .map { view -> mapper(Row(view)) }
                .list()
                .asSequence()
        }
    }

}