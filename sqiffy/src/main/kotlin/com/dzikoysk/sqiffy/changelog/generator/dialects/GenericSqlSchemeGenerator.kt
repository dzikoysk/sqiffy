package com.dzikoysk.sqiffy.changelog.generator.dialects

import com.dzikoysk.sqiffy.changelog.Enums
import com.dzikoysk.sqiffy.changelog.generator.SqlSchemeGenerator
import com.dzikoysk.sqiffy.definition.DataType
import com.dzikoysk.sqiffy.definition.DataType.BOOLEAN
import com.dzikoysk.sqiffy.definition.DataType.CHAR
import com.dzikoysk.sqiffy.definition.DataType.DATE
import com.dzikoysk.sqiffy.definition.DataType.DOUBLE
import com.dzikoysk.sqiffy.definition.DataType.ENUM
import com.dzikoysk.sqiffy.definition.DataType.FLOAT
import com.dzikoysk.sqiffy.definition.DataType.INT
import com.dzikoysk.sqiffy.definition.DataType.LONG
import com.dzikoysk.sqiffy.definition.DataType.SERIAL
import com.dzikoysk.sqiffy.definition.DataType.TEXT
import com.dzikoysk.sqiffy.definition.DataType.UUID_TYPE
import com.dzikoysk.sqiffy.definition.DataType.VARCHAR
import com.dzikoysk.sqiffy.definition.PropertyData

abstract class GenericSqlSchemeGenerator : SqlSchemeGenerator {

    /* Table */

    override fun createTable(name: String, properties: List<PropertyData>, enums: Enums): String =
        """CREATE TABLE IF NOT EXISTS ${name.toQuoted()} (${properties.joinToString(separator = ", ") { "${it.name.toQuoted()} ${createDataTypeWithAttributes(it, enums)}"}});"""

    override fun renameTable(currentName: String, renameTo: String): String =
        """ALTER TABLE ${currentName.toQuoted()} RENAME ${renameTo.toQuoted()}"""

    /* Columns */

    override fun createColumn(tableName: String, property: PropertyData, enums: Enums): String =
         "ALTER TABLE ${tableName.toQuoted()} ADD ${property.name.toQuoted()} ${createDataTypeWithAttributes(property, enums)}"

    override fun renameColumn(tableName: String, currentName: String, renameTo: String): String =
        "ALTER TABLE ${tableName.toQuoted()} RENAME COLUMN ${currentName.toQuoted()} TO ${renameTo.toQuoted()}"

    override fun retypeColumn(tableName: String, oldProperty: PropertyData, newProperty: PropertyData, enums: Enums): String =
        "ALTER TABLE ${tableName.toQuoted()} MODIFY ${newProperty.name.toQuoted()} ${createDataTypeWithAttributes(newProperty, enums)}"

    override fun removeColumn(tableName: String, columnName: String): String =
        "ALTER TABLE ${tableName.toQuoted()} DROP COLUMN ${columnName.toQuoted()}"

    /* Constraints */

    override fun createPrimaryKey(tableName: String, name: String, on: List<PropertyData>): String =
        """ALTER TABLE ${tableName.toQuoted()} ADD CONSTRAINT ${name.toQuoted()} PRIMARY KEY (${on.joinToString(separator = ", ") { it.name.toQuoted() }})"""

    override fun removePrimaryKey(tableName: String, name: String): String =
        """ALTER TABLE ${tableName.toQuoted()} DROP PRIMARY KEY"""

    override fun createForeignKey(tableName: String, name: String, on: PropertyData, foreignTable: String, foreignColumn: PropertyData): String =
        """ALTER TABLE ${tableName.toQuoted()} ADD CONSTRAINT ${name.toQuoted()} FOREIGN KEY (${on.name.toQuoted()}) REFERENCES ${foreignTable.toQuoted()} (${foreignColumn.name.toQuoted()})"""

    override fun removeForeignKey(tableName: String, name: String): String =
        """ALTER TABLE ${tableName.toQuoted()} DROP FOREIGN KEY ${name.toQuoted()}"""

    /* Indices */

    override fun createIndex(tableName: String, name: String, on: List<String>): String =
        """CREATE INDEX ${name.toQuoted()} ON ${tableName.toQuoted()} (${createIndexColumns(on)})"""

    override fun createUniqueIndex(tableName: String, name: String, on: List<String>): String =
        """CREATE UNIQUE INDEX ${name.toQuoted()} ON ${tableName.toQuoted()} (${createIndexColumns(on)})"""

    /* Utilities */

    abstract fun createDataType(property: PropertyData, availableEnums: Enums): String

    protected fun createRegularDataType(property: PropertyData): String =
        with(property) {
            when (type) {
                CHAR -> {
                    require(details != null) { "Missing char size in '${property.name}' property" }
                    "CHAR($details)"
                }
                VARCHAR -> {
                    require(details != null) { "Missing varchar size in '${property.name}' property" }
                    "VARCHAR($details)"
                }
                TEXT -> "TEXT"
                BOOLEAN -> "BOOLEAN"
                INT -> "INT"
                LONG -> "BIGINT"
                FLOAT -> "FLOAT"
                DOUBLE -> "DOUBLE"
                DATE -> "DATE"
                else -> throw UnsupportedOperationException("Cannot create data type based on $property")
            }
        }

    protected open fun createDataTypeWithAttributes(property: PropertyData, availableEnums: Enums): String =
        createDataType(property, availableEnums)
            .let { dataType -> "$dataType ${property.default?.let { default -> "DEFAULT ${default.toSqlDefault(property)} " } ?: ""}" }
            .let { if (!property.nullable) "$it NOT NULL" else it }

    private fun String.toSqlDefault(property: PropertyData): String =
        when (property.rawDefault) {
            true -> property.default!!
            else ->
                createSqlDefault(this, property)
                    ?: createRegularDefault(this, property)
                    ?: throw UnsupportedOperationException("Cannot create default value based on $this")
        }


    abstract fun createSqlDefault(rawDefault: String, property: PropertyData, dataType: DataType = property.type!!): String?

    private fun createRegularDefault(rawDefault: String, property: PropertyData, dataType: DataType = property.type!!): String? =
        when (dataType) {
            SERIAL, BOOLEAN, INT, LONG, FLOAT, DOUBLE -> rawDefault
            UUID_TYPE, ENUM, CHAR, VARCHAR, TEXT -> "'$rawDefault'"
            else -> null
        }

    private fun createIndexColumns(columns: List<String>): String =
        columns.joinToString(separator = ", ") { it.toQuoted() }

    open fun String.toQuoted(): String =
        "\"$this\""

}