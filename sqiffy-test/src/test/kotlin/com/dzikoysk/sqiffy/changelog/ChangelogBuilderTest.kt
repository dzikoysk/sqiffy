package com.dzikoysk.sqiffy.changelog

import com.dzikoysk.sqiffy.changelog.generator.dialects.MySqlSchemeGenerator
import com.dzikoysk.sqiffy.definition.NamingStrategy.RAW
import com.dzikoysk.sqiffy.e2e.GuildDefinition
import com.dzikoysk.sqiffy.e2e.UserDefinition
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertDoesNotThrow

class ChangelogBuilderTest {

    @Test
    fun testBaseSchemeGenerator() {
        val baseSchemeGenerator = ChangelogBuilder(MySqlSchemeGenerator, RAW)
        val changeLog = baseSchemeGenerator.generateChangeLogAtRuntime(tables = listOf(UserDefinition::class, GuildDefinition::class))

        changeLog.getAllChanges().forEach { (version, changes) ->
            println(version)
            changes.forEach { println("  $it") }
        }
    }

    @Test
    fun `should ignore duplicated classes`() {
        assertDoesNotThrow {
            val builder = ChangelogBuilder(MySqlSchemeGenerator, RAW)
            builder.generateChangeLogAtRuntime(tables = listOf(UserDefinition::class, UserDefinition::class))
        }
    }

}