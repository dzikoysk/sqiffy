package com.dzikoysk.sqiffy.changelog.generator.dialects

import com.dzikoysk.sqiffy.changelog.EnumState
import com.dzikoysk.sqiffy.changelog.Enums
import com.dzikoysk.sqiffy.definition.DataType
import com.dzikoysk.sqiffy.definition.DataType.BINARY
import com.dzikoysk.sqiffy.definition.DataType.BOOLEAN
import com.dzikoysk.sqiffy.definition.DataType.DATE
import com.dzikoysk.sqiffy.definition.DataType.DATETIME
import com.dzikoysk.sqiffy.definition.DataType.ENUM
import com.dzikoysk.sqiffy.definition.DataType.SERIAL
import com.dzikoysk.sqiffy.definition.DataType.TEXT
import com.dzikoysk.sqiffy.definition.DataType.TIMESTAMP
import com.dzikoysk.sqiffy.definition.DataType.UUID_TYPE
import com.dzikoysk.sqiffy.definition.PropertyData
import com.dzikoysk.sqiffy.shared.multiline

object MySqlSchemeGenerator : GenericSqlSchemeGenerator() {

    override fun createTable(name: String, properties: List<PropertyData>, enums: Enums): String {
        val types = properties.joinToString(separator = ", ") { propertyData ->
            val type = "${propertyData.name.toQuoted()} ${createDataTypeWithAttributes(propertyData, enums)}";

            when (propertyData.type) {
                SERIAL -> "$type, KEY (${propertyData.name.toQuoted()})"
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
                SERIAL -> "INT AUTO_INCREMENT"
                UUID_TYPE -> "VARCHAR(36)"
                ENUM ->
                    enumDefinition
                        ?.let { availableEnums.getEnum(it.type) }
                        ?.values
                        ?.joinToString { "'$it'" }
                        ?.let { "ENUM($it)" }
                        ?: throw IllegalStateException("Missing enum data for property $name")
                BINARY -> {
                    require(details != null) { "Missing binary size in '$name' property" }
                    "BINARY($details)"
                }
                TIMESTAMP -> "TIMESTAMP"
                DATETIME -> "DATETIME"
                else -> createRegularDataType(property)
            }
        }

    override fun createSqlDefault(rawDefault: String, property: PropertyData, dataType: DataType): String? {
        return when (dataType) {
            BINARY -> "'$rawDefault'"
            DATE -> "STR_TO_DATE('$rawDefault','%Y-%m-%d')"
            DATETIME -> "STR_TO_DATE('$rawDefault','%Y-%m-%dT%H:%i:%s')"
            TIMESTAMP -> "STR_TO_DATE('$rawDefault','%Y-%m-%dT%H:%i:%s.%fZ')"
            else -> null
        }
    }

    override fun removeIndex(tableName: String, name: String): String =
        "DROP INDEX ${name.toQuoted()} ON ${tableName.toQuoted()}"

    override fun String.toQuoted(): String = "`$this`"

}