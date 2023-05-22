package com.dzikoysk.sqiffy.e2e

import com.dzikoysk.sqiffy.definition.DataType.SERIAL
import com.dzikoysk.sqiffy.definition.Definition
import com.dzikoysk.sqiffy.definition.DefinitionVersion
import com.dzikoysk.sqiffy.definition.Property
import com.dzikoysk.sqiffy.e2e.specification.SqiffyE2ETestSpecification
import com.dzikoysk.sqiffy.e2e.specification.postgresDataSource
import com.zaxxer.hikari.HikariDataSource
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertDoesNotThrow

internal class EmbeddedPostgresMigratorE2ETest : MigratorE2ETest() {
    private val postgres = postgresDataSource()
    override fun createDataSource(): HikariDataSource = postgres.dataSource
    @AfterEach
    fun stop() { postgres.embeddedPostgres.close() }
}

internal abstract class MigratorE2ETest : SqiffyE2ETestSpecification(runMigrations = false)  {

    @Definition([
        DefinitionVersion(
            name = "table1",
            version = "1.0.0",
            properties = [
                Property(name = "id", type = SERIAL),
            ]
        )
    ])
    private object Table1Definition

    @Definition([
        DefinitionVersion(
            name = "table2",
            version = "1.0.1",
            properties = [
                Property(name = "id", type = SERIAL),
            ]
        )
    ])
    private object Table2Definition

    @Test
    fun `should run all migrations if migration table does not exist`() {
        // given: database scheme with two versions
        val changeLog = database.generateChangeLog(Table1Definition::class, Table2Definition::class)
        assertThat(changeLog.getAllChanges()).hasSize(2)

        // when: migrations are run against empty database
        val appliedVersionsV1 = assertDoesNotThrow { database.runMigrations(changeLog = changeLog) }
        // then: all migrations are applied
        assertThat(appliedVersionsV1).isEqualTo(listOf("1.0.0", "1.0.1"))

        // when: migrations are run again
        val appliedVersionsV2 = assertDoesNotThrow { database.runMigrations(changeLog = changeLog) }
        // then: no migrations are applied
        assertThat(appliedVersionsV2).isEmpty()
    }

}