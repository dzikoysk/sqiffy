package com.dzikoysk.sqiffy.changelog

import com.dzikoysk.sqiffy.definition.DefinitionEntry
import com.dzikoysk.sqiffy.definition.EnumReference

typealias TableName = String
typealias Version = String
typealias Query = String

data class ChangeLog(
    val enums: Map<EnumReference, EnumState>,
    val tables: Map<DefinitionEntry, TableName>,
    private val enumChanges: List<SchemeChange>,
    private val schemeChanges: List<SchemeChange>
) {

    fun getAllChanges(): List<SchemeChange> =
        (enumChanges + schemeChanges)
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
