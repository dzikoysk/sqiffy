package com.dzikoysk.sqiffy.changelog.generator.dialects

import com.dzikoysk.sqiffy.changelog.EnumState
import com.dzikoysk.sqiffy.changelog.Enums
import com.dzikoysk.sqiffy.definition.DataType
import com.dzikoysk.sqiffy.definition.DataType.BINARY
import com.dzikoysk.sqiffy.definition.DataType.DATE
import com.dzikoysk.sqiffy.definition.DataType.DATETIME
import com.dzikoysk.sqiffy.definition.DataType.DOUBLE
import com.dzikoysk.sqiffy.definition.DataType.ENUM
import com.dzikoysk.sqiffy.definition.DataType.SERIAL
import com.dzikoysk.sqiffy.definition.DataType.TIMESTAMP
import com.dzikoysk.sqiffy.definition.DataType.UUID_TYPE
import com.dzikoysk.sqiffy.definition.PropertyData

object SqliteSchemeGenerator : GenericSqlSchemeGenerator() {

    override fun createEnum(name: String, values: List<String>): String? =
        null

    override fun addEnumValues(enum: EnumState, values: List<String>, inUse: List<Pair<String, PropertyData>>): String? =
        null

    override fun createPrimaryKey(tableName: String, name: String, on: List<PropertyData>): String =
        """
            pragma writable_schema=1;
            update sqlite_master 
            set sql = replace(
                sql, 
                ');', 
                ', primary key (${on.joinToString { it.name.toQuoted() }}));'
            ) 
            where type='table' and name=${tableName.toQuoted()};
        """.trimIndent()

    override fun createForeignKey(tableName: String, name: String, on: PropertyData, foreignTable: String, foreignColumn: PropertyData): String =
        """
            pragma writable_schema=1;
            update sqlite_master 
            set sql = replace(
                sql, 
                '${on.name.toQuoted()} ${createDataTypeWithAttributes(on, Enums())}', 
                '${on.name.toQuoted()} ${createDataTypeWithAttributes(on, Enums())}, foreign key (${on.name.toQuoted()}) references ${foreignTable.toQuoted()}(${foreignColumn.name.toQuoted()})'
            ) 
            where type='table' and name=${tableName.toQuoted()};
            pragma foreign_keys=on;
        """.trimIndent()

    override fun removeForeignKey(tableName: String, name: String): String =
        ""

    override fun retypeColumn(tableName: String, oldProperty: PropertyData, newProperty: PropertyData, enums: Enums): String =
        """
            pragma writable_schema=1;
            update sqlite_master 
            set sql = replace(
                sql, 
                '${oldProperty.name.toQuoted()} ${createDataTypeWithAttributes(oldProperty, enums)}', 
                '${newProperty.name.toQuoted()} ${createDataTypeWithAttributes(newProperty, enums)}'
            ) 
            where type='table' and name=${tableName.toQuoted()};
        """.trimIndent()

    override fun removeIndex(tableName: String, name: String): String =
        """DROP INDEX ${name.toQuoted()}"""

    override fun createDataType(property: PropertyData, availableEnums: Enums): String =
        when (property.type) {
            SERIAL -> "INTEGER PRIMARY KEY AUTOINCREMENT"
            UUID_TYPE -> "TEXT"
            ENUM -> "TEXT"
            BINARY -> "TEXT"
            DOUBLE -> "REAL"
            DATE -> "TEXT"
            DATETIME -> "TEXT"
            TIMESTAMP -> "TEXT"
            else -> createRegularDataType(property)
        }

    override fun createDataTypeWithAttributes(property: PropertyData, availableEnums: Enums): String =
        createDataType(property, availableEnums).let {
            if (!property.nullable && property.type != SERIAL) "$it NOT NULL"
            else it
        }

    override fun createSqlDefault(rawDefault: String, property: PropertyData, dataType: DataType): String? =
        when (dataType) {
            BINARY -> "'$rawDefault'"
            DATE, DATETIME, TIMESTAMP -> "'$rawDefault'"
            else -> null
        }

}