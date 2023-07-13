package com.dzikoysk.sqiffy.changelog.generator.dialects

import com.dzikoysk.sqiffy.changelog.EnumState
import com.dzikoysk.sqiffy.changelog.Enums
import com.dzikoysk.sqiffy.definition.DataType.BINARY
import com.dzikoysk.sqiffy.definition.DataType.DATETIME
import com.dzikoysk.sqiffy.definition.DataType.ENUM
import com.dzikoysk.sqiffy.definition.DataType.SERIAL
import com.dzikoysk.sqiffy.definition.DataType.UUID_TYPE
import com.dzikoysk.sqiffy.definition.PropertyData
import com.dzikoysk.sqiffy.shared.multiline

object PostgreSqlSchemeGenerator : GenericSqlSchemeGenerator() {

    override fun retypeColumn(tableName: String, oldProperty: PropertyData, newProperty: PropertyData, enums: Enums): String =
        multiline(
            """
            ALTER TABLE ${tableName.toQuoted()} ALTER COLUMN ${newProperty.name.toQuoted()} SET DATA TYPE ${createDataType(newProperty, enums)};
            ALTER TABLE ${tableName.toQuoted()} ALTER COLUMN ${newProperty.name.toQuoted()} ${
                when {
                    newProperty.nullable -> "DROP NOT NULL;"
                    else -> "SET NOT NULL;"
                }
            }
        """
        )

    override fun createEnum(name: String, values: List<String>): String =
        multiline(
            """
            CREATE TYPE ${name.toQuoted()} AS ENUM (
               ${values.joinToString(separator = ",\n") { "'$it'" }}
            );
        """
        )

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
            SERIAL -> "SERIAL"
            UUID_TYPE -> "UUID"
            ENUM -> property.enumDefinition
                ?.let { enums.getEnum(it.type) }
                ?.name
                ?.toQuoted()
                ?: throw IllegalStateException("Missing enum data for property '${property.name}'")
            DATETIME -> "TIMESTAMPTZ"
            BINARY -> "BYTEA"
            else -> createRegularDataType(property)
        }

}