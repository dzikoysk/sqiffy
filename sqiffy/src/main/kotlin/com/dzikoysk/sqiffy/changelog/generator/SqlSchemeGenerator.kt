package com.dzikoysk.sqiffy.changelog.generator

import com.dzikoysk.sqiffy.changelog.EnumState
import com.dzikoysk.sqiffy.changelog.Enums
import com.dzikoysk.sqiffy.definition.PropertyData

interface SqlSchemeGenerator {

    /* Table */

    fun createTable(name: String, properties: List<PropertyData>, enums: Enums): String

    fun renameTable(currentName: String, renameTo: String): String

    /* Columns */

    fun createColumn(tableName: String, property: PropertyData, enums: Enums): String

    fun renameColumn(tableName: String, currentName: String, renameTo: String): String

    fun retypeColumn(tableName: String, oldProperty: PropertyData, newProperty: PropertyData, enums: Enums): String

    fun removeColumn(tableName: String, columnName: String): String

    /* Enums */

    fun createEnum(name: String, values: List<String>): String?

    fun addEnumValues(enum: EnumState, values: List<String>, inUse: List<Pair<String, PropertyData>>): String?

    /* Constraints */

    fun createPrimaryKey(tableName: String, name: String, on: List<PropertyData>): String

    fun removePrimaryKey(tableName: String, name: String): String

    fun createForeignKey(tableName: String, name: String, on: PropertyData, foreignTable: String, foreignColumn: PropertyData): String

    fun removeForeignKey(tableName: String, name: String): String

    /* Indices */

    fun createIndex(tableName: String, name: String, on: List<String>): String

    fun createUniqueIndex(tableName: String, name: String, on: List<String>): String

    fun removeIndex(tableName: String, name: String): String

    /* Functions */

    fun createFunction(name: String, parameters: Array<String>, returnType: String, body: String): String

}


