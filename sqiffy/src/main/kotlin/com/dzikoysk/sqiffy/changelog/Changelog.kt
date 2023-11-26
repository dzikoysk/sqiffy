package com.dzikoysk.sqiffy.changelog

import com.dzikoysk.sqiffy.definition.EnumReference
import com.dzikoysk.sqiffy.definition.FunctionDefinitionData
import com.dzikoysk.sqiffy.definition.FunctionVersionData
import com.dzikoysk.sqiffy.definition.ParsedDefinition

typealias TableName = String
typealias Version = String
typealias Query = String

data class Changelog(
    val enums: Map<EnumReference, EnumState>,
    val functions: Map<FunctionDefinitionData, FunctionVersionData>,
    val tables: Map<ParsedDefinition, TableName>,
    private val enumChanges: List<SchemeChange>,
    private val functionChanges: List<SchemeChange>,
    private val schemeChanges: List<SchemeChange>
) {

    fun getAllChanges(): List<SchemeChange> =
        (enumChanges + functionChanges + schemeChanges)
            .groupBy { it.version }
            .mapValues { (version, schemeChanges) ->
                SchemeChange(
                    version = version,
                    changes = schemeChanges.flatMap { it.changes }
                )
            }
            .values
            .sortedBy { it.version }

}

data class SchemeChange(
    val version: Version,
    val changes: List<Change>
)

data class Change(
    val description: String,
    val query: Query,
)
