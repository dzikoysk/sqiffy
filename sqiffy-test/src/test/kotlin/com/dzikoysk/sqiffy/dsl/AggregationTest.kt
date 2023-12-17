package com.dzikoysk.sqiffy.dsl

import com.dzikoysk.sqiffy.dialect.MySqlDatabase
import com.dzikoysk.sqiffy.Sqiffy
import com.dzikoysk.sqiffy.definition.DataType
import com.dzikoysk.sqiffy.definition.Definition
import com.dzikoysk.sqiffy.definition.DefinitionVersion
import com.dzikoysk.sqiffy.definition.Property
import com.dzikoysk.sqiffy.migrator.SqiffyMigrator
import com.dzikoysk.sqiffy.shared.H2Mode
import com.dzikoysk.sqiffy.shared.createH2DataSource
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

internal class AggregationTest {

    @Definition(
        versions = [
            DefinitionVersion(
                name = "count-test",
                version = "1.0.0",
                properties = [
                    Property(name = "id", type = DataType.SERIAL),
                ]
            )
        ]
    )
    private object TestCountDefinition

    @Test
    fun `should count records`() {
        val database = Sqiffy.createDatabase<MySqlDatabase>(dataSource = createH2DataSource(mode = H2Mode.MYSQL))
        database.runMigrations(SqiffyMigrator(database.generateChangeLog(tables = listOf(TestCountDefinition::class))))
        database.insert(TestCountTable).values().map {  }
        database.insert(TestCountTable).values().map {  }
        database.insert(TestCountTable).values().map {  }
        assertThat(database.select(TestCountTable).slice(TestCountTable.id.count()).map { it[TestCountTable.id.count()] }.firstOrNull()).isEqualTo(3)
    }

}