package com.dzikoysk.sqiffy.generator

import com.dzikoysk.sqiffy.DefinitionEntry

class BaseSchemeGenerator {

    fun generateChangeLog(tables: List<DefinitionEntry>) {
        val byVersion = tables
            .flatMap { entry ->
                entry.definition.value.map { entry to it }
            }
            .groupBy { it.second.version }
            .toSortedMap()

        val baseScheme = byVersion[byVersion.firstKey()]!!
        println(baseScheme)

    }

}