package com.dzikoysk.sqiffy.definition

import com.dzikoysk.sqiffy.definition.DataType.NULL_TYPE
import com.dzikoysk.sqiffy.definition.PropertyDefinitionOperation.ADD

enum class PropertyDefinitionOperation {
    ADD,
    RENAME,
    RETYPE,
    REMOVE
}

@Target()
annotation class Property(
    val operation: PropertyDefinitionOperation = ADD,
    val name: String,
    val type: DataType = NULL_TYPE,
    val details: String = NULL_STRING,
    val rename: String = NULL_STRING,
    val nullable: Boolean = false,
)

data class PropertyData(
    val name: String,
    val type: DataType?,
    val details: String? = null,
    val nullable: Boolean = false,
)

fun Property.toPropertyData(): PropertyData =
    PropertyData(
        name = name,
        type = type.takeIf { it != NULL_TYPE },
        details = details.takeIf { it != NULL_STRING },
        nullable = nullable,
    )
