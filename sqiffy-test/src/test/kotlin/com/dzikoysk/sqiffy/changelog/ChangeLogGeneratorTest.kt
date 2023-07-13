package com.dzikoysk.sqiffy.changelog

import com.dzikoysk.sqiffy.GuildDefinition
import com.dzikoysk.sqiffy.UserDefinition
import com.dzikoysk.sqiffy.changelog.generator.dialects.MySqlSchemeGenerator
import com.dzikoysk.sqiffy.definition.RuntimeTypeFactory
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertDoesNotThrow

class ChangeLogGeneratorTest {

    @Test
    fun testBaseSchemeGenerator() {
        val baseSchemeGenerator = ChangeLogGenerator(MySqlSchemeGenerator, RuntimeTypeFactory())
        val changeLog = baseSchemeGenerator.generateChangeLog(UserDefinition::class, GuildDefinition::class)

        changeLog.getAllChanges().forEach { (version, changes) ->
            println(version)
            changes.forEach { println("  $it") }
        }
    }

    @Test
    fun `should ignore duplicated classes`() {
        assertDoesNotThrow {
            ChangeLogGenerator(MySqlSchemeGenerator, RuntimeTypeFactory()).generateChangeLog(UserDefinition::class, UserDefinition::class)
        }
    }

}