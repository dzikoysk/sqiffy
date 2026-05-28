package com.dzikoysk.sqiffy.dsl

import com.dzikoysk.sqiffy.Sqiffy
import com.dzikoysk.sqiffy.definition.DataType
import com.dzikoysk.sqiffy.definition.Definition
import com.dzikoysk.sqiffy.definition.DefinitionVersion
import com.dzikoysk.sqiffy.definition.Property
import com.dzikoysk.sqiffy.dialect.MySqlDatabase
import com.dzikoysk.sqiffy.migrator.SqiffyMigrator
import com.dzikoysk.sqiffy.shared.H2Mode
import com.dzikoysk.sqiffy.shared.createH2DataSource
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

@Definition(
    versions = [
        DefinitionVersion(
            name = "raw_query_test",
            version = "1.0.0",
            properties = [
                Property(name = "id", type = DataType.SERIAL),
                Property(name = "name", type = DataType.VARCHAR, details = "64"),
            ]
        )
    ]
)
private object RawQueryTestDefinition

internal class DslRawQueryTest {

    @Test
    fun `should execute raw query without args`() {
        val database = Sqiffy.createDatabase<MySqlDatabase>(dataSource = createH2DataSource(mode = H2Mode.MYSQL))
        database.runMigrations(SqiffyMigrator(database.generateChangeLog(tables = listOf(RawQueryTestDefinition::class))))

        database.insert(RawQueryTestTable).values(name = "panda").map { }
        database.insert(RawQueryTestTable).values(name = "bamboo").map { }

        val names = database.rawQuery("SELECT name FROM raw_query_test ORDER BY name") { rs -> rs.getString("name") }
        assertThat(names).containsExactly("bamboo", "panda")
    }

    @Test
    fun `should execute raw query with named args`() {
        val database = Sqiffy.createDatabase<MySqlDatabase>(dataSource = createH2DataSource(mode = H2Mode.MYSQL))
        database.runMigrations(SqiffyMigrator(database.generateChangeLog(tables = listOf(RawQueryTestDefinition::class))))

        database.insert(RawQueryTestTable).values(name = "panda").map { }
        database.insert(RawQueryTestTable).values(name = "bamboo").map { }

        val names = database.rawQuery(
            sql = "SELECT name FROM raw_query_test WHERE name = :name",
            args = mapOf("name" to "panda"),
        ) { rs -> rs.getString("name") }
        assertThat(names).containsExactly("panda")
    }

    @Test
    fun `should return empty list when no rows match`() {
        val database = Sqiffy.createDatabase<MySqlDatabase>(dataSource = createH2DataSource(mode = H2Mode.MYSQL))
        database.runMigrations(SqiffyMigrator(database.generateChangeLog(tables = listOf(RawQueryTestDefinition::class))))

        val names = database.rawQuery("SELECT name FROM raw_query_test") { rs -> rs.getString("name") }
        assertThat(names).isEmpty()
    }
}
