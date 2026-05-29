package com.dzikoysk.sqiffy.dsl

import com.dzikoysk.sqiffy.specification.H2Target
import com.dzikoysk.sqiffy.specification.IntegrationSpecification
import com.dzikoysk.sqiffy.specification.MariaDbTarget
import com.dzikoysk.sqiffy.specification.MySqlTarget
import com.dzikoysk.sqiffy.specification.PostgresTarget
import com.dzikoysk.sqiffy.specification.SqliteTarget
import com.dzikoysk.sqiffy.specification.TypesTable
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith

@ExtendWith(H2Target::class) internal class H2ColumnTypesTest : ColumnTypesTest()
@ExtendWith(SqliteTarget::class) internal class SqliteColumnTypesTest : ColumnTypesTest()
@ExtendWith(PostgresTarget::class) internal class PostgresColumnTypesTest : ColumnTypesTest()
@ExtendWith(MySqlTarget::class) internal class MySqlColumnTypesTest : ColumnTypesTest()
@ExtendWith(MariaDbTarget::class) internal class MariaDbColumnTypesTest : ColumnTypesTest()

internal abstract class ColumnTypesTest : IntegrationSpecification() {

    private val day = LocalDate.of(2021, 6, 15)
    private val moment = LocalDateTime.of(2021, 6, 15, 13, 30, 45)
    private val instant = Instant.parse("2021-06-15T13:30:45Z")

    @Test
    fun `should round-trip every supported column type`() {
        val id = database.insert(TypesTable) {
            it[TypesTable.label] = "hello world"
            it[TypesTable.amount] = Int.MAX_VALUE
            it[TypesTable.counter] = 9_000_000_000L
            it[TypesTable.ratio] = 1.5
            it[TypesTable.flag] = true
            it[TypesTable.day] = day
            it[TypesTable.moment] = moment
            it[TypesTable.instant] = instant
        }.map { it[TypesTable.id] }.first()

        val row = database.select(TypesTable).where { TypesTable.id eq id }.map {
            mapOf(
                "label" to it[TypesTable.label],
                "amount" to it[TypesTable.amount],
                "counter" to it[TypesTable.counter],
                "ratio" to it[TypesTable.ratio],
                "flag" to it[TypesTable.flag],
                "day" to it[TypesTable.day],
                "moment" to it[TypesTable.moment],
                "instant" to it[TypesTable.instant],
            )
        }.first()

        assertThat(row["label"]).isEqualTo("hello world")
        assertThat(row["amount"]).isEqualTo(Int.MAX_VALUE)
        assertThat(row["counter"]).isEqualTo(9_000_000_000L)
        assertThat(row["ratio"]).isEqualTo(1.5)
        assertThat(row["flag"]).isEqualTo(true)
        assertThat(row["day"]).isEqualTo(day)
        assertThat(row["moment"]).isEqualTo(moment)
        assertThat(row["instant"]).isEqualTo(instant)
    }

    @Test
    fun `should round-trip a false boolean`() {
        val id = database.insert(TypesTable) {
            it[TypesTable.label] = "off"
            it[TypesTable.amount] = 0
            it[TypesTable.counter] = 0L
            it[TypesTable.ratio] = 0.0
            it[TypesTable.flag] = false
            it[TypesTable.day] = day
            it[TypesTable.moment] = moment
            it[TypesTable.instant] = instant
        }.map { it[TypesTable.id] }.first()

        val flag = database.select(TypesTable).where { TypesTable.id eq id }.map { it[TypesTable.flag] }.first()
        assertThat(flag).isFalse()
    }

}
