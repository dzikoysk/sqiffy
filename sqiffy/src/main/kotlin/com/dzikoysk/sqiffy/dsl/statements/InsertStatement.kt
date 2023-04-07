package com.dzikoysk.sqiffy.dsl.statements

import com.dzikoysk.sqiffy.SqiffyDatabase
import com.dzikoysk.sqiffy.dsl.Column
import com.dzikoysk.sqiffy.dsl.Row
import com.dzikoysk.sqiffy.dsl.Statement
import com.dzikoysk.sqiffy.dsl.Table
import com.dzikoysk.sqiffy.dsl.generator.ParameterAllocator
import com.dzikoysk.sqiffy.dsl.generator.QueryColumn
import org.slf4j.event.Level

open class InsertStatement(
    protected val database: SqiffyDatabase,
    protected val table: Table,
    protected val values: Map<Column<*>, Any?>
) : Statement {

    fun <T> map(mapper: (Row) -> T): Sequence<T> {
        return database.getJdbi().withHandle<Sequence<T>, Exception> { handle ->
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
                .map { view ->
                    mapper(Row(view))
                }
                .list()
                .asSequence()
        }
    }

}