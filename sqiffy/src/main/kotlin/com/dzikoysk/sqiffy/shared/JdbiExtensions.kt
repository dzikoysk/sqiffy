package com.dzikoysk.sqiffy.shared

import com.dzikoysk.sqiffy.dsl.Aggregation
import com.dzikoysk.sqiffy.dsl.Column
import com.dzikoysk.sqiffy.dsl.distinctModifier
import org.jdbi.v3.core.argument.AbstractArgumentFactory
import org.jdbi.v3.core.config.ConfigRegistry
import org.jdbi.v3.core.mapper.ColumnMapper
import org.jdbi.v3.core.mapper.MappingException
import org.jdbi.v3.core.result.RowView
import org.jdbi.v3.core.statement.StatementContext
import java.sql.PreparedStatement
import java.sql.ResultSet
import java.sql.Types
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
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
            getColumn("${type.aggregationFunction}(${distinctModifier()}$rawIdentifier)", resultType) // get column from generated alias
        } catch (mappingException: MappingException) {
            getColumn("${type.aggregationFunction}(${distinctModifier()}$fallbackAlias)", resultType) // default column name fallback
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

/*
 * SQLite has no native temporal types and stores them as ISO-8601 TEXT, so java.time values are
 * bound and read as strings - the same approach UUIDArgumentFactory takes for the missing UUID type.
 */

class TextArgument(private val value: String) : JdbiArgument {
    override fun apply(position: Int, statement: PreparedStatement, ctx: StatementContext) {
        statement.setString(position, value)
    }
}

class LocalDateArgumentFactory : AbstractArgumentFactory<LocalDate>(Types.VARCHAR) {
    override fun build(value: LocalDate, config: ConfigRegistry?): JdbiArgument = TextArgument(value.toString())
}

class LocalDateTimeArgumentFactory : AbstractArgumentFactory<LocalDateTime>(Types.VARCHAR) {
    override fun build(value: LocalDateTime, config: ConfigRegistry?): JdbiArgument = TextArgument(value.toString())
}

class InstantArgumentFactory : AbstractArgumentFactory<Instant>(Types.VARCHAR) {
    override fun build(value: Instant, config: ConfigRegistry?): JdbiArgument = TextArgument(value.toString())
}

object LocalDateColumnMapper : ColumnMapper<LocalDate> {
    override fun map(resultSet: ResultSet, columnNumber: Int, ctx: StatementContext): LocalDate? =
        resultSet.getString(columnNumber)?.let { LocalDate.parse(it) }
}

object LocalDateTimeColumnMapper : ColumnMapper<LocalDateTime> {
    override fun map(resultSet: ResultSet, columnNumber: Int, ctx: StatementContext): LocalDateTime? =
        resultSet.getString(columnNumber)?.let { LocalDateTime.parse(it) }
}

object InstantColumnMapper : ColumnMapper<Instant> {
    override fun map(resultSet: ResultSet, columnNumber: Int, ctx: StatementContext): Instant? =
        resultSet.getString(columnNumber)?.let { Instant.parse(it) }
}