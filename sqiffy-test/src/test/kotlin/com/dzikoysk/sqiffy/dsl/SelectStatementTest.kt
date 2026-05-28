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
            name = "exists_test",
            version = "1.0.0",
            properties = [
                Property(name = "id", type = DataType.SERIAL),
                Property(name = "name", type = DataType.VARCHAR, details = "64"),
            ]
        )
    ]
)
private object ExistsTestDefinition

internal class SelectStatementTest {

    @Test
    fun `exists should return false on empty table`() {
        val database = Sqiffy.createDatabase<MySqlDatabase>(dataSource = createH2DataSource(mode = H2Mode.MYSQL))
        database.runMigrations(SqiffyMigrator(database.generateChangeLog(tables = listOf(ExistsTestDefinition::class))))

        assertThat(database.select(ExistsTestTable).exists()).isFalse()
    }

    @Test
    fun `exists should return true when rows match`() {
        val database = Sqiffy.createDatabase<MySqlDatabase>(dataSource = createH2DataSource(mode = H2Mode.MYSQL))
        database.runMigrations(SqiffyMigrator(database.generateChangeLog(tables = listOf(ExistsTestDefinition::class))))

        database.insert(ExistsTestTable).values(name = "panda").map { }

        assertThat(database.select(ExistsTestTable).exists()).isTrue()
        assertThat(database.select(ExistsTestTable).where { ExistsTestTable.name eq "panda" }.exists()).isTrue()
        assertThat(database.select(ExistsTestTable).where { ExistsTestTable.name eq "missing" }.exists()).isFalse()
    }
}
