package com.dzikoysk.sqiffy.changelog

typealias Version = String
typealias Query = String

class ChangeLog(
    val changes: List<VersionChange>
)

data class VersionChange(
    val version: Version,
    val changes: List<Query>
)

data class Change(
    val query: Query
)