package com.dzikoysk.sqiffy.changelog

import com.dzikoysk.sqiffy.definition.DataType
import com.dzikoysk.sqiffy.definition.PropertyData
import com.dzikoysk.sqiffy.shared.multiline
import com.dzikoysk.sqiffy.shared.toQuoted

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

    override fun createEnum(name: String, values: List<String>): String? =
        null

    override fun addEnumValues(enum: EnumState, values: List<String>, inUse: List<Pair<String, PropertyData>>): String =
        inUse.joinToString("\n") { (tableName, property) ->
            multiline("""ALTER TABLE "$tableName" MODIFY COLUMN "${property.name}" ENUM(${enum.values.joinToString(separator = ", ") { it.toQuoted().replace("\"", "'") }});""")
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
        """DROP INDEX "$name" ON "$tableName""""

}

object PostgreSqlSchemeGenerator : GenericSqlSchemeGenerator() {

    override fun retypeColumn(tableName: String, property: PropertyData, enums: Enums): String =
         multiline("""
            ALTER TABLE "$tableName" ALTER COLUMN "${property.name}" SET DATA TYPE ${createDataType(property, enums)};
            ALTER TABLE "$tableName" ALTER COLUMN "${property.name}" ${
                when {
                    property.nullable -> "DROP NOT NULL;"
                    else -> "SET NOT NULL;"
                }
            }
        """)

    override fun createEnum(name: String, values: List<String>): String =
        multiline("""
            CREATE TYPE "$name" AS ENUM (
                ${values.joinToString(separator = ",\n") { "'$it'" }}
            );
        """)

    override fun addEnumValues(enum: EnumState, values: List<String>, inUse: List<Pair<String, PropertyData>>): String =
        listOf(values.first()).joinToString(separator = "\n") {
            """ALTER TYPE "${enum.name}" ADD VALUE '$it';"""
        }

    override fun removeForeignKey(tableName: String, name: String): String =
        """ALTER TABLE "$tableName" DROP CONSTRAINT "$name""""

    override fun removeIndex(tableName: String, name: String): String =
        """DROP INDEX "$name""""

    override fun createDataType(property: PropertyData, enums: Enums): String =
        when (property.type) {
            DataType.SERIAL -> "SERIAL"
            DataType.UUID_TYPE -> "UUID"
            DataType.ENUM -> property.enumDefinition
                ?.let { enums.getEnum(it.type) }
                ?.name
                ?.toQuoted()
                ?: throw IllegalStateException("Missing enum data for property '${property.name}'")
            DataType.DATETIME -> "TIMESTAMPTZ"
            DataType.BINARY -> "BYTEA"
            else -> createRegularDataType(property)
        }

}

abstract class GenericSqlSchemeGenerator : SqlSchemeGenerator {

    /* Table */

    override fun createTable(name: String, properties: List<PropertyData>, enums: Enums): String =
        """CREATE TABLE IF NOT EXISTS "$name" (${properties.joinToString(separator = ", ") { "${it.name.toQuoted()} ${createDataTypeWithAttributes(it, enums)}"}});"""

    override fun renameTable(currentName: String, renameTo: String): String =
        """ALTER TABLE "$currentName" RENAME "$renameTo""""

    /* Columns */

    override fun createColumn(tableName: String, property: PropertyData, enums: Enums): String =
         """ALTER TABLE "$tableName" ADD "${property.name}" ${createDataTypeWithAttributes(property, enums)}"""

    override fun renameColumn(tableName: String, currentName: String, renameTo: String): String =
        """ALTER TABLE "$tableName" RENAME COLUMN "$currentName" TO "$renameTo""""

    override fun retypeColumn(tableName: String, property: PropertyData, enums: Enums): String =
        """ALTER TABLE "$tableName" MODIFY "${property.name}" ${createDataTypeWithAttributes(property, enums)}"""

    override fun removeColumn(tableName: String, columnName: String): String =
        """ALTER TABLE "$tableName" DROP COLUMN "$columnName""""

    /* Constraints */

    override fun createPrimaryKey(tableName: String, name: String, on: List<String>): String =
        """ALTER TABLE "$tableName" ADD CONSTRAINT "$name" PRIMARY KEY (${on.joinToString(separator = ", ") { it.toQuoted() }})"""

    override fun removePrimaryKey(tableName: String, name: String): String =
        """ALTER TABLE "$tableName" DROP PRIMARY KEY"""

    override fun createForeignKey(tableName: String, name: String, on: String, foreignTable: String, foreignColumn: String): String =
        """ALTER TABLE "$tableName" ADD CONSTRAINT "$name" FOREIGN KEY ("$on") REFERENCES "$foreignTable" ("$foreignColumn")"""

    override fun removeForeignKey(tableName: String, name: String): String =
        """ALTER TABLE "$tableName" DROP FOREIGN KEY "$name""""

    /* Indices */

    override fun createIndex(tableName: String, name: String, on: List<String>): String =
        """CREATE INDEX "$name" ON "$tableName" (${createIndexColumns(on)})"""

    override fun createUniqueIndex(tableName: String, name: String, on: List<String>): String =
        """CREATE UNIQUE INDEX "$name" ON "$tableName" (${createIndexColumns(on)})"""

    /* Utilities */

    abstract fun createDataType(property: PropertyData, availableEnums: Enums): String

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

    private fun createDataTypeWithAttributes(property: PropertyData, availableEnums: Enums): String =
        with (property) {
            var dataType = createDataType(property, availableEnums)

            if (!nullable) {
                dataType += " NOT NULL"
            }

            dataType
        }

    private fun createIndexColumns(columns: List<String>): String =
        columns.joinToString(separator = ", ") { "\"$it\"" }

}


