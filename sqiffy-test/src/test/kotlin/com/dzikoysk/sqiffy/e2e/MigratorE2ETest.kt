package com.dzikoysk.sqiffy.e2e

import com.dzikoysk.sqiffy.changelog.VersionCallbacks
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

    private object SchemeVersion {
        const val V_1_0_0 = "1.0.0"
        const val V_1_0_1 = "1.0.1"
    }

    @Definition([
        DefinitionVersion(
            name = "table1",
            version = SchemeVersion.V_1_0_0,
            properties = [
                Property(name = "id", type = SERIAL),
            ]
        )
    ])
    private object Table1Definition

    @Definition([
        DefinitionVersion(
            name = "table2",
            version = SchemeVersion.V_1_0_1,
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
        val appliedVersionsV1 = assertDoesNotThrow { database.runMigrations(changeLog) }
        // then: all migrations are applied
        assertThat(appliedVersionsV1).isEqualTo(listOf("1.0.0", "1.0.1"))

        // when: migrations are run again
        val appliedVersionsV2 = assertDoesNotThrow { database.runMigrations(changeLog) }
        // then: no migrations are applied
        assertThat(appliedVersionsV2).isEmpty()
    }

    @Test
    fun `should run version callback`() {
        // given: database scheme with two versions
        val changeLog = database.generateChangeLog(Table1Definition::class, Table2Definition::class)
        assertThat(changeLog.getAllChanges()).hasSize(2)

        // when: migrations are run against empty database
        val values = mutableListOf<Int>()
        val appliedVersionsV1 = assertDoesNotThrow {
            database.runMigrations(
                changeLog = changeLog,
                versionCallbacks = VersionCallbacks()
                    .before(SchemeVersion.V_1_0_0) { values.add(1) }
                    .after(SchemeVersion.V_1_0_0) { values.add(2) }
                    .before(SchemeVersion.V_1_0_1) { values.add(3) }
                    .after(SchemeVersion.V_1_0_1) { values.add(4) }
            )
        }
        // then: all migrations are applied
        assertThat(appliedVersionsV1).isEqualTo(listOf("1.0.0", "1.0.1"))
        assertThat(values).isEqualTo(listOf(1, 2, 3, 4))
    }

}