package com.dzikoysk.sqiffy.changelog

import com.dzikoysk.sqiffy.definition.DataType
import com.dzikoysk.sqiffy.definition.PropertyData
import com.dzikoysk.sqiffy.shared.multiline

interface SqlSchemeGenerator {

    /* Table */

    fun createTable(name: String, properties: List<PropertyData>, enums: Enums): String

    fun renameTable(currentName: String, renameTo: String): String

    /* Columns */

    fun createColumn(tableName: String, property: PropertyData, enums: Enums): String

    fun renameColumn(tableName: String, currentName: String, renameTo: String): String

    fun retypeColumn(tableName: String, property: PropertyData, enums: Enums): String

    fun removeColumn(tableName: String, columnName: String): String

    /* Enums */

    fun createEnum(name: String, values: List<String>): String?

    fun addEnumValues(enum: EnumState, values: List<String>, inUse: List<Pair<String, PropertyData>>): String

    /* Constraints */

    fun createPrimaryKey(tableName: String, name: String, on: List<String>): String

    fun removePrimaryKey(tableName: String, name: String): String

    fun createForeignKey(tableName: String, name: String, on: String, foreignTable: String, foreignColumn: String): String

    fun removeForeignKey(tableName: String, name: String): String

    /* Indices */

    fun createIndex(tableName: String, name: String, on: List<String>): String

    fun createUniqueIndex(tableName: String, name: String, on: List<String>): String

    fun removeIndex(tableName: String, name: String): String

}

object MySqlSchemeGenerator : GenericSqlSchemeGenerator() {

    override fun createTable(name: String, properties: List<PropertyData>, enums: Enums): String {
        val types = properties.joinToString(separator = ", ") { propertyData ->
            val type = "${propertyData.name.toQuoted()} ${createDataTypeWithAttributes(propertyData, enums)}";

            when (propertyData.type) {
                DataType.SERIAL -> "$type, KEY ${propertyData.name.toQuoted()} (${propertyData.name.toQuoted()})"
                else -> type
            }
        }

        return "CREATE TABLE IF NOT EXISTS ${name.toQuoted()} ($types);"
    }

    override fun createEnum(name: String, values: List<String>): String? =
        null

    override fun addEnumValues(enum: EnumState, values: List<String>, inUse: List<Pair<String, PropertyData>>): String =
        inUse.joinToString("\n") { (tableName, property) ->
            multiline("ALTER TABLE ${tableName.toQuoted()} MODIFY COLUMN ${property.name.toQuoted()} ENUM(${enum.values.joinToString(separator = ", ") { "'$it'" }});")
        }

    override fun createDataType(property: PropertyData, availableEnums: Enums): String =
        with (property) {
            when (type) {
                DataType.SERIAL -> "INT AUTO_INCREMENT"
                DataType.UUID_TYPE -> "VARCHAR(36)"
                DataType.ENUM ->
                    enumDefinition
                        ?.let { availableEnums.getEnum(it.type) }
                        ?.values
                        ?.joinToString { "'$it'" }
                        ?.let { "ENUM($it)" }
                        ?: throw IllegalStateException("Missing enum data for property $name")
                DataType.DATETIME -> "DATETIME"
                DataType.BINARY -> {
                    require(details != null) { "Missing binary size in '$name' property" }
                    "BINARY($details)"
                }
                else -> createRegularDataType(property)
            }
        }

    override fun removeIndex(tableName: String, name: String): String =
        "DROP INDEX ${name.toQuoted()} ON ${tableName.toQuoted()}"

    override fun String.toQuoted(): String = "`$this`"

}

object PostgreSqlSchemeGenerator : GenericSqlSchemeGenerator() {

    override fun retypeColumn(tableName: String, property: PropertyData, enums: Enums): String =
         multiline("""
            ALTER TABLE ${tableName.toQuoted()} ALTER COLUMN ${property.name.toQuoted()} SET DATA TYPE ${createDataType(property, enums)};
            ALTER TABLE ${tableName.toQuoted()} ALTER COLUMN ${property.name.toQuoted()} ${
                when {
                    property.nullable -> "DROP NOT NULL;"
                    else -> "SET NOT NULL;"
                }
            }
        """)

    override fun createEnum(name: String, values: List<String>): String =
        multiline("""
            CREATE TYPE ${name.toQuoted()} AS ENUM (
               ${values.joinToString(separator = ",\n") { "'$it'" }}
            );
        """)

    override fun addEnumValues(enum: EnumState, values: List<String>, inUse: List<Pair<String, PropertyData>>): String =
        listOf(values.first()).joinToString(separator = "\n") {
            """ALTER TYPE ${enum.name.toQuoted()} ADD VALUE '$it';"""
        }

    override fun removeForeignKey(tableName: String, name: String): String =
        """ALTER TABLE ${tableName.toQuoted()} DROP CONSTRAINT ${name.toQuoted()}"""

    override fun removeIndex(tableName: String, name: String): String =
        """DROP INDEX ${name.toQuoted()}"""

    override fun createDataType(property: PropertyData, enums: Enums): String =
        when (property.type) {
            DataType.SERIAL -> "SERIAL"
            DataType.UUID_TYPE -> "UUID"
            DataType.ENUM -> property.enumDefinition
                ?.let { enums.getEnum(it.type) }
                ?.name
                ?.let { it.toQuoted() }
                ?: throw IllegalStateException("Missing enum data for property '${property.name}'")
            DataType.DATETIME -> "TIMESTAMPTZ"
            DataType.BINARY -> "BYTEA"
            else -> createRegularDataType(property)
        }

}

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

    override fun retypeColumn(tableName: String, property: PropertyData, enums: Enums): String =
        "ALTER TABLE ${tableName.toQuoted()} MODIFY ${property.name.toQuoted()} ${createDataTypeWithAttributes(property, enums)}"

    override fun removeColumn(tableName: String, columnName: String): String =
        "ALTER TABLE ${tableName.toQuoted()} DROP COLUMN ${columnName.toQuoted()}"

    /* Constraints */

    override fun createPrimaryKey(tableName: String, name: String, on: List<String>): String =
        """ALTER TABLE ${tableName.toQuoted()} ADD CONSTRAINT ${name.toQuoted()} PRIMARY KEY (${on.joinToString(separator = ", ") { it.toQuoted() }})"""

    override fun removePrimaryKey(tableName: String, name: String): String =
        """ALTER TABLE ${tableName.toQuoted()} DROP PRIMARY KEY"""

    override fun createForeignKey(tableName: String, name: String, on: String, foreignTable: String, foreignColumn: String): String =
        """ALTER TABLE ${tableName.toQuoted()} ADD CONSTRAINT ${name.toQuoted()} FOREIGN KEY (${on.toQuoted()}) REFERENCES ${foreignTable.toQuoted()} (${foreignColumn.toQuoted()})"""

    override fun removeForeignKey(tableName: String, name: String): String =
        """ALTER TABLE ${tableName.toQuoted()} DROP FOREIGN KEY ${name.toQuoted()}"""

    /* Indices */

    override fun createIndex(tableName: String, name: String, on: List<String>): String =
        """CREATE INDEX ${name.toQuoted()} ON ${tableName.toQuoted()} (${createIndexColumns(on)})"""

    override fun createUniqueIndex(tableName: String, name: String, on: List<String>): String =
        """CREATE UNIQUE INDEX ${name.toQuoted()} ON ${tableName.toQuoted()} (${createIndexColumns(on)})"""

    /* Utilities */

    abstract fun createDataType(property: PropertyData, availableEnums: Enums): String
    
    open fun String.toQuoted(): String = "\"$this\""

    protected fun createRegularDataType(property: PropertyData): String =
        with (property) {
            when (type) {
                DataType.CHAR -> {
                    require(details != null) { "Missing char size in '${property.name}' property" }
                    "CHAR($details)"
                }
                DataType.VARCHAR -> {
                    require(details != null) { "Missing varchar size in '${property.name}' property" }
                    "VARCHAR($details)"
                }
                DataType.TEXT -> "TEXT"
                DataType.BOOLEAN -> "BOOLEAN"
                DataType.INT -> "INT"
                DataType.LONG -> "BIGINT"
                DataType.FLOAT -> "FLOAT"
                DataType.DOUBLE -> "DOUBLE"
                DataType.DATE -> "DATE"
                DataType.TIMESTAMP -> "TIMESTAMP"
                else -> throw UnsupportedOperationException("Cannot create data type based on $property")
            }
        }

    protected fun createDataTypeWithAttributes(property: PropertyData, availableEnums: Enums): String =
        with (property) {
            var dataType = createDataType(property, availableEnums)

            if (!nullable) {
                dataType += " NOT NULL"
            }

            dataType
        }

    private fun createIndexColumns(columns: List<String>): String =
        columns.joinToString(separator = ", ") { it.toQuoted() }

}


