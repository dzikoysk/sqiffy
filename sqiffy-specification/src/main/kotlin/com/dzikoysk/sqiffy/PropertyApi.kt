package com.dzikoysk.sqiffy

import com.dzikoysk.sqiffy.DataType.NULL_TYPE

enum class PropertyDefinitionType {
    ADD,
    RENAME,
    RETYPE,
    REMOVE
}

@Target()
annotation class Property(
    val definitionType: PropertyDefinitionType = PropertyDefinitionType.ADD,
    val name: String,
    val type: DataType = NULL_TYPE,
    val details: String = NULL_STRING,
    val rename: String = NULL_STRING,
    val retypeType: DataType = NULL_TYPE,
    val retypeDetails: String = NULL_STRING,
    val nullable: Boolean = false,
    val autoincrement: Boolean = false
)

data class PropertyData(
    val name: String,
    val type: DataType?,
    val details: String?,
    val nullable: Boolean,
    val autoIncrement: Boolean,
)

fun Property.toPropertyData(): PropertyData =
    PropertyData(
        name = name,
        type = type.takeIf { it != NULL_TYPE },
        details = details.takeIf { it != NULL_STRING },
        nullable = nullable,
        autoIncrement = autoincrement
    )
