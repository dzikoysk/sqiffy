package com.dzikoysk.sqiffy.changelog

import com.dzikoysk.sqiffy.definition.EnumReference
import com.dzikoysk.sqiffy.definition.TypeDefinition

class Enums(
    private val availableEnums: () -> Map<TypeDefinition, EnumState> = { emptyMap() },
    val defineEnum: (EnumReference) -> Unit = {}
) {
    fun getEnum(enumType: TypeDefinition): EnumState? = availableEnums()[enumType]
}

data class EnumState(
    val name: String,
    val values: List<String>
)
