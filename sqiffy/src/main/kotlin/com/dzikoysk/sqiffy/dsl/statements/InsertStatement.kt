package com.dzikoysk.sqiffy.dsl.statements

import com.dzikoysk.sqiffy.SqiffyDatabase
import com.dzikoysk.sqiffy.dsl.Column
import com.dzikoysk.sqiffy.dsl.ParameterAllocator
import com.dzikoysk.sqiffy.dsl.SqlQueryGenerator.QueryColumn
import com.dzikoysk.sqiffy.dsl.Statement
import com.dzikoysk.sqiffy.dsl.Table
import org.jdbi.v3.core.result.ResultBearing
import org.jdbi.v3.core.result.ResultIterable
import org.slf4j.event.Level

class InsertStatement(
    val database: SqiffyDatabase,
    val table: Table,
    val values: Map<Column<*>, Any?>
) : Statement<ResultBearing> {

    override fun <R> execute(mapper: (ResultBearing) -> ResultIterable<R>): Sequence<R> {
        return database.getJdbi().withHandle<Sequence<R>, Exception> { handle ->
            val allocator = ParameterAllocator()
            val columns = values.mapKeys { it.key.name }

            val (query, args) = database.sqlQueryGenerator.createInsertQuery(
                allocator = allocator,
                tableName = table.getTableName(),
                columns = values.keys.map {
                    QueryColumn(
                        table = it.table.getTableName(),
                        name = it.name,
                        dbType = it.dbType,
                        type = it.type
                    )
                }
            )

            database.logger.log(Level.DEBUG, "Executing query: $query with arguments: $args")

            handle
                .createUpdate(query)
                .also {
                    args.forEach { (arg, column) ->
                        val value = columns[column]!!
                        it.bindByType(arg, value, value::class.javaObjectType)
                    }
                }
                .executeAndReturnGeneratedKeys()
                .let { mapper(it) }
                .list()
                .asSequence()
        }
    }

}