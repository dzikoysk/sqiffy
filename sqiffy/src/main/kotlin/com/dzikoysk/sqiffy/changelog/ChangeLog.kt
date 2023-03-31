package com.dzikoysk.sqiffy.changelog

import com.dzikoysk.sqiffy.definition.DefinitionEntry

typealias TableName = String
typealias Version = String
typealias Query = String

data class ChangeLog(
    val tables: Map<DefinitionEntry, TableName>,
    val changes: List<VersionChange>
)

data class VersionChange(
    val version: Version,
    val changes: List<Change>
)

data class Change(
    val query: Query
)
