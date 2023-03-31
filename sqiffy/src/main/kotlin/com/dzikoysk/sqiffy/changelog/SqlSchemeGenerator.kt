package com.dzikoysk.sqiffy.changelog

import com.dzikoysk.sqiffy.definition.DataType
import com.dzikoysk.sqiffy.definition.PropertyData
import com.dzikoysk.sqiffy.shared.multiline

interface SqlSchemeGenerator {

    /* Table */

    fun createTable(name: String, properties: List<PropertyData>): String

    fun renameTable(currentName: String, renameTo: String): String

    /* Columns */

    fun createColumn(tableName: String, property: PropertyData): String

    fun renameColumn(tableName: String, currentName: String, renameTo: String): String

    fun retypeColumn(tableName: String, property: PropertyData): String

    fun removeColumn(tableName: String, columnName: String): String

    /* Constraints */

    fun createPrimaryKey(tableName: String, name: String, on: String): String

    fun removePrimaryKey(tableName: String, name: String): String

    fun createForeignKey(tableName: String, name: String, on: String, foreignTable: String, foreignColumn: String): String

    fun removeForeignKey(tableName: String, name: String): String

    /* Indices */

    fun createIndex(tableName: String, name: String, on: List<String>): String

    fun createUniqueIndex(tableName: String, name: String, on: List<String>): String

    fun removeIndex(tableName: String, name: String): String

}

object MySqlSchemeGenerator : GenericSqlSchemeGenerator() {

    override fun createDataType(property: PropertyData): String =
        when (property.type) {
            DataType.SERIAL -> "INT AUTO_INCREMENT"
            DataType.UUID_TYPE -> "VARCHAR(36)"
            else -> createRegularDataType(property)
        }

}

object PostgreSqlSchemeGenerator : GenericSqlSchemeGenerator() {

    override fun retypeColumn(tableName: String, property: PropertyData): String =
        multiline("""
            ALTER TABLE "$tableName" ALTER COLUMN "${property.name}" SET DATA TYPE ${createDataType(property)};
            ALTER TABLE "$tableName" ALTER COLUMN "${property.name}" ${
                when {
                    property.nullable -> """DROP NOT NULL;"""
                    else -> """SET NOT NULL;"""
                }
            }
        """)

    override fun removeForeignKey(tableName: String, name: String): String =
        """ALTER TABLE "$tableName" DROP CONSTRAINT "$name""""

    override fun createDataType(property: PropertyData): String =
        when (property.type) {
            DataType.SERIAL -> "SERIAL"
            DataType.UUID_TYPE -> "UUID"
            else -> createRegularDataType(property)
        }

}

abstract class GenericSqlSchemeGenerator : SqlSchemeGenerator {

    /* Table */

    override fun createTable(name: String, properties: List<PropertyData>): String =
        """CREATE TABLE IF NOT EXISTS "$name" (${properties.joinToString(separator = ", ") { "\"${it.name}\" ${createDataTypeWithAttributes(it)}" }})"""

    override fun renameTable(currentName: String, renameTo: String): String =
        """ALTER TABLE "$currentName" RENAME "$renameTo""""

    /* Columns */

    override fun createColumn(tableName: String, property: PropertyData): String =
        """ALTER TABLE "$tableName" ADD "${property.name}" ${createDataTypeWithAttributes(property)}"""

    override fun renameColumn(tableName: String, currentName: String, renameTo: String): String =
        """ALTER TABLE "$tableName" RENAME COLUMN "$currentName" TO "$renameTo""""

    override fun retypeColumn(tableName: String, property: PropertyData): String =
        """ALTER TABLE "$tableName" MODIFY "${property.name}" ${createDataTypeWithAttributes(property)}"""

    override fun removeColumn(tableName: String, columnName: String): String =
        """ALTER TABLE "$tableName" DROP COLUMN "$columnName""""

    /* Constraints */

    override fun createPrimaryKey(tableName: String, name: String, on: String): String =
        """ALTER TABLE "$tableName" ADD CONSTRAINT "$name" PRIMARY KEY ("$on")"""

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

    override fun removeIndex(tableName: String, name: String): String =
        """DROP INDEX "$name" ON "$tableName""""

    /* Utilities */

    abstract fun createDataType(property: PropertyData): String

    protected fun createRegularDataType(property: PropertyData): String =
        with (property) {
            when (type) {
            DataType.CHAR -> "CHAR($details)"
                DataType.VARCHAR -> "VARCHAR($details)"
                DataType.BINARY -> "BINARY($details)"
                DataType.TEXT -> "TEXT"
                DataType.BOOLEAN -> "BOOLEAN"
                DataType.INT -> "INT"
                DataType.LONG -> "BIGINT"
                DataType.FLOAT -> "FLOAT"
                DataType.DOUBLE -> "DOUBLE"
                DataType.DATE -> "DATE"
                DataType.DATETIME -> "DATETIME"
                DataType.TIMESTAMP -> "TIMESTAMP"
                else -> throw UnsupportedOperationException("Cannot create data type based on $property")
            }
        }

    private fun createDataTypeWithAttributes(property: PropertyData): String =
        with (property) {
            var dataType = createDataType(property)

            if (!nullable) {
                dataType += " NOT NULL"
            }

            dataType
        }

    private fun createIndexColumns(columns: List<String>): String =
        columns.joinToString(separator = ", ") { "\"$it\"" }

}


