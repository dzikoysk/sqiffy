package com.dzikoysk.sqiffy.sql

import com.dzikoysk.sqiffy.DataType.BINARY
import com.dzikoysk.sqiffy.DataType.BLOB
import com.dzikoysk.sqiffy.DataType.BOOLEAN
import com.dzikoysk.sqiffy.DataType.CHAR
import com.dzikoysk.sqiffy.DataType.DATE
import com.dzikoysk.sqiffy.DataType.DATETIME
import com.dzikoysk.sqiffy.DataType.DOUBLE
import com.dzikoysk.sqiffy.DataType.FLOAT
import com.dzikoysk.sqiffy.DataType.INT
import com.dzikoysk.sqiffy.DataType.TEXT
import com.dzikoysk.sqiffy.DataType.TIMESTAMP
import com.dzikoysk.sqiffy.DataType.UUID_BINARY
import com.dzikoysk.sqiffy.DataType.UUID_VARCHAR
import com.dzikoysk.sqiffy.DataType.VARCHAR
import com.dzikoysk.sqiffy.PropertyData

interface SqlGenerator {

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

abstract class GenericSqlGenerator : SqlGenerator {

    override fun createTable(name: String, properties: List<PropertyData>): String =
        """CREATE TABLE "$name" (${properties.joinToString(separator = ", ") { "\"${it.name}\" ${createDataType(it)}" }})"""

    override fun renameTable(currentName: String, renameTo: String): String =
        """ALTER TABLE "$currentName" RENAME "$renameTo""""

    override fun createColumn(tableName: String, property: PropertyData): String =
        """ALTER TABLE "$tableName" ADD "${property.name}" ${createDataType(property)}"""

    override fun renameColumn(tableName: String, currentName: String, renameTo: String): String =
        """ALTER TABLE "$tableName" RENAME COLUMN "$currentName" TO "$renameTo""""

    override fun retypeColumn(tableName: String, property: PropertyData): String =
        """ALTER TABLE "$tableName" MODIFY "${property.name}" ${createDataType(property)}"""

    override fun removeColumn(tableName: String, columnName: String): String =
        """ALTER TABLE "$tableName" DROP COLUMN "$columnName""""

    override fun createPrimaryKey(tableName: String, name: String, on: String): String =
        """ALTER TABLE "$tableName" ADD CONSTRAINT "$name" PRIMARY KEY ("$on")"""

    override fun removePrimaryKey(tableName: String, name: String): String =
        """ALTER TABLE "$tableName" DROP PRIMARY KEY"""

    override fun createForeignKey(tableName: String, name: String, on: String, foreignTable: String, foreignColumn: String): String =
        """ALTER TABLE "$tableName" ADD CONSTRAINT "$name" FOREIGN KEY ("$on") REFERENCES "$foreignTable"("$foreignColumn")"""

    override fun removeForeignKey(tableName: String, name: String): String =
        """ALTER TABLE "$tableName" DROP FOREIGN KEY "$name""""

    override fun createIndex(tableName: String, name: String, on: List<String>): String =
        """CREATE INDEX "$name" ON "$tableName" (${createIndexColumns(on)})"""

    override fun createUniqueIndex(tableName: String, name: String, on: List<String>): String =
        """CREATE UNIQUE INDEX "$name" ON "$tableName" (${createIndexColumns(on)})"""

    override fun removeIndex(tableName: String, name: String): String =
        """DROP INDEX "$name" ON "$tableName""""

    private fun createDataType(property: PropertyData): String =
        with (property) {
            var dataType = when (type) {
                CHAR -> "CHAR($details)"
                UUID_BINARY -> "BINARY(16)"
                UUID_VARCHAR -> "VARCHAR(36)"
                VARCHAR -> "VARCHAR($details)"
                BINARY -> "BINARY($details)"
                TEXT -> "TEXT"
                BLOB -> "BLOB"
                BOOLEAN -> "BOOLEAN"
                INT -> "INT"
                FLOAT -> "FLOAT"
                DOUBLE -> "DOUBLE"
                DATE -> "DATE"
                DATETIME -> "DATETIME"
                TIMESTAMP -> "TIMESTAMP"
                else -> throw UnsupportedOperationException("Cannot create data type based on $property")
            }

            if (!nullable) {
                dataType += " NOT NULL"
            }

            if (autoIncrement) {
                dataType += " AUTO_INCREMENT"
            }

            dataType
        }

    private fun createIndexColumns(columns: List<String>): String =
        columns.joinToString(separator = ", ") { "\"$it\"" }

}

object MySqlGenerator : GenericSqlGenerator()

object PostgreSqlGenerator : GenericSqlGenerator()
