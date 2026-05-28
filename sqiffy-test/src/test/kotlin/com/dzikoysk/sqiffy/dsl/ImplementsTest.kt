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

interface HasId {
    val id: Long
}

@Definition(
    implements = [HasId::class],
    versions = [
        DefinitionVersion(
            name = "implements_test",
            version = "1.0.0",
            properties = [
                Property(name = "id", type = DataType.BIGSERIAL),
                Property(name = "name", type = DataType.VARCHAR, details = "64"),
            ]
        )
    ]
)
private object ImplementsTestDefinition

internal class DefinitionImplementsTest {

    @Test
    fun `should generate entity implementing the interface`() {
        val database = Sqiffy.createDatabase<MySqlDatabase>(dataSource = createH2DataSource(mode = H2Mode.MYSQL))
        database.runMigrations(SqiffyMigrator(database.generateChangeLog(tables = listOf(ImplementsTestDefinition::class))))

        database.insert(ImplementsTestTable).values(name = "panda").map { }

        val entity: ImplementsTest = database.select(ImplementsTestTable).map { it.toImplementsTest() }.first()
        val asInterface: HasId = entity

        assertThat(asInterface.id).isEqualTo(1L)
    }
}
