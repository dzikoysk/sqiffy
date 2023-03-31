package com.dzikoysk.sqiffy.changelog

import com.dzikoysk.sqiffy.GuildDefinition
import com.dzikoysk.sqiffy.UserDefinition
import com.dzikoysk.sqiffy.definition.RuntimeTypeFactory
import org.junit.jupiter.api.Test

class ChangeLogGeneratorTest {

    @Test
    fun testBaseSchemeGenerator() {
        val baseSchemeGenerator = ChangeLogGenerator(MySqlSchemeGenerator, RuntimeTypeFactory())
        val changeLog = baseSchemeGenerator.generateChangeLog(UserDefinition::class, GuildDefinition::class)

        changeLog.changes.forEach { (version, changes) ->
            println(version)
            changes.forEach { println("  $it") }
        }
    }

}