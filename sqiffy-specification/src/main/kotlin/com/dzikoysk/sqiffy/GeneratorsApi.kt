package com.dzikoysk.sqiffy

data class DefinitionEntry(
    val source: String,
    val packageName: String,
    val name: String,
    val definition: Definition,
)