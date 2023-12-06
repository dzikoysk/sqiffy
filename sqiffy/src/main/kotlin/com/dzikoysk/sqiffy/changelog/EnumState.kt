package com.dzikoysk.sqiffy.changelog

import com.dzikoysk.sqiffy.definition.EnumDefinitionData
import com.dzikoysk.sqiffy.definition.TypeDefinition

class Enums(
    private val availableEnums: () -> Map<TypeDefinition, EnumState> = { emptyMap() },
    val defineEnum: (EnumDefinitionData) -> Unit = {}
) {
    fun getEnum(enumType: TypeDefinition): EnumState? = availableEnums()[enumType]
    override fun toString(): String = availableEnums().toString()
}

data class EnumState(
    val name: String,
    val values: List<String>
)
