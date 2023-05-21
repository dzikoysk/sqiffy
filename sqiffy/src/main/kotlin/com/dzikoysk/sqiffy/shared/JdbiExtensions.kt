package com.dzikoysk.sqiffy.shared

import com.dzikoysk.sqiffy.dsl.Aggregation
import com.dzikoysk.sqiffy.dsl.Column
import org.jdbi.v3.core.argument.AbstractArgumentFactory
import org.jdbi.v3.core.config.ConfigRegistry
import org.jdbi.v3.core.mapper.MappingException
import org.jdbi.v3.core.result.RowView
import org.jdbi.v3.core.statement.StatementContext
import java.sql.PreparedStatement
import java.sql.Types
import java.util.UUID
import org.jdbi.v3.core.argument.Argument as JdbiArgument

fun multiline(text: String): String =
    text.trimIndent().replace("\n", " ").trim()

operator fun <T> RowView.get(column: Column<T>): T =
    try {
        getColumn(column.rawIdentifier, column.type) // get column from generated alias
    } catch (mappingException: MappingException) {
        getColumn(column.name, column.type) // default column name fallback
    }

operator fun <T> RowView.get(aggregation: Aggregation<T>): T =
    with (aggregation) {
        try {
            getColumn("${type.aggregationFunction}($rawIdentifier)", resultType) // get column from generated alias
        } catch (mappingException: MappingException) {
            getColumn("${type.aggregationFunction}($fallbackAlias)", resultType) // default column name fallback
        }
    }

class UUIDArgumentFactory(sqlType: Int = Types.VARCHAR) : AbstractArgumentFactory<UUID>(sqlType) {
    override fun build(value: UUID, config: ConfigRegistry?): JdbiArgument = UUIDArgument(value)
}

class UUIDArgument(private val value: UUID) : JdbiArgument {

    override fun apply(position: Int, statement: PreparedStatement, ctx: StatementContext) {
        statement.setString(position, value.toString())
    }

}