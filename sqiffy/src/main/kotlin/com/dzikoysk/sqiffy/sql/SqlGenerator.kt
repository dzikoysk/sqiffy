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
import com.dzikoysk.sqiffy.DataType.SERIAL
import com.dzikoysk.sqiffy.DataType.TEXT
import com.dzikoysk.sqiffy.DataType.TIMESTAMP
import com.dzikoysk.sqiffy.DataType.UUID_TYPE
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

    /* Queries */

    fun createSelectQuery(tableName: String, columns: List<String>, where: String? = null): String

    fun createInsertQuery(tableName: String, columns: List<String>): String

    fun createUpdateQuery(tableName: String, columns: List<String>, where: String? = null): String

}

object MySqlGenerator : GenericSqlGenerator() {

    override fun createDataType(property: PropertyData): String =
        when (property.type) {
            SERIAL -> "INT AUTO_INCREMENT"
            UUID_TYPE -> "VARCHAR(36)"
            else -> createRegularDataType(property)
        }

}

object PostgreSqlGenerator : GenericSqlGenerator() {

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
            SERIAL -> "SERIAL"
            UUID_TYPE -> "UUID"
            else -> createRegularDataType(property)
        }

}

abstract class GenericSqlGenerator : SqlGenerator {

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

    /* Queries */

    override fun createSelectQuery(tableName: String, columns: List<String>, where: String?): String =
        multiline("""
            SELECT ${columns.joinToString(separator = ", ") { it.toQuoted() }}
            FROM "$tableName"
            ${where?.let { "WHERE $it" } ?: ""}
        """)

    override fun createInsertQuery(tableName: String, columns: List<String>): String =
        multiline("""
            INSERT INTO "$tableName" (${columns.joinToString(separator = ", ") { it.toQuoted() }})
            VALUES (${columns.joinToString(separator = ", ") { ":$it" }})
        """)

    override fun createUpdateQuery(tableName: String, columns: List<String>, where: String?): String =
        multiline("""
            UPDATE "$tableName"
            SET ${columns.joinToString(separator = ", ") { it.toQuoted() + " = :$it" }}
            ${where?.let { "WHERE $it" } ?: ""}
        """)

    /* Utilities */

    abstract fun createDataType(property: PropertyData): String

    protected fun createRegularDataType(property: PropertyData): String =
        with (property) {
            when (type) {
                CHAR -> "CHAR($details)"
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

private fun String.toQuoted(): String =
    "\"$this\""

private fun multiline(text: String): String =
    text.trimIndent().replace("\n", " ")
