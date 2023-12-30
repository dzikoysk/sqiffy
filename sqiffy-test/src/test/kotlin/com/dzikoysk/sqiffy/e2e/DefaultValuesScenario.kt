package com.dzikoysk.sqiffy.e2e

import com.dzikoysk.sqiffy.definition.DataType.BINARY
import com.dzikoysk.sqiffy.definition.DataType.BOOLEAN
import com.dzikoysk.sqiffy.definition.DataType.CHAR
import com.dzikoysk.sqiffy.definition.DataType.DATE
import com.dzikoysk.sqiffy.definition.DataType.DATETIME
import com.dzikoysk.sqiffy.definition.DataType.DECIMAL
import com.dzikoysk.sqiffy.definition.DataType.DOUBLE
import com.dzikoysk.sqiffy.definition.DataType.ENUM
import com.dzikoysk.sqiffy.definition.DataType.FLOAT
import com.dzikoysk.sqiffy.definition.DataType.INT
import com.dzikoysk.sqiffy.definition.DataType.LONG
import com.dzikoysk.sqiffy.definition.DataType.NUMERIC
import com.dzikoysk.sqiffy.definition.DataType.TEXT
import com.dzikoysk.sqiffy.definition.DataType.TIMESTAMP
import com.dzikoysk.sqiffy.definition.DataType.UUID_TYPE
import com.dzikoysk.sqiffy.definition.DataType.VARCHAR
import com.dzikoysk.sqiffy.definition.Definition
import com.dzikoysk.sqiffy.definition.DefinitionVersion
import com.dzikoysk.sqiffy.definition.Property

//TODO: Move to standalone test
object DefaultConstants {
    const val UUID_DEFAULT = "00000000-0000-0000-0000-000000000000"
    const val ENUM_DEFAULT = "USER"
    const val CHAR_DEFAULT = 'a'
    const val VARCHAR_DEFAULT = "abcdefghijklmnopqrstuvwxyz"
    const val BINARY_DEFAULT = "abcdefghijklmnopqrstuvwxyz"
    const val TEXT_DEFAULT = "Test text"
    const val BOOLEAN_DEFAULT = true
    const val INT_DEFAULT = 2147483647
    const val LONG_DEFAULT = 9223372036854775807L
    const val FLOAT_DEFAULT = 3.4028235E38F
    const val NUMERIC_DEFAULT = "1.23"
    const val DECIMAL_DEFAULT = "1.23"
    const val DOUBLE_DEFAULT = 1.7976931348623157E308
    const val DATE_DEFAULT = "1975-12-05"
    const val DATETIME_DEFAULT = "2016-12-03T16:01:51"
    const val TIMESTAMP_DEFAULT = "2005-04-02T21:37:21.37Z"
}

@Definition(
    domainPackage = "com.dzikoysk.sqiffy.domain",
    infrastructurePackage = "com.dzikoysk.sqiffy.infra",
    apiPackage = "com.dzikoysk.sqiffy.api",
    versions = [
        DefinitionVersion(
            version = "1.0.0",
            name = "test_default_table",
            properties = [
                Property(name = "uuid", type = UUID_TYPE, default = DefaultConstants.UUID_DEFAULT),
                Property(name = "enum", type = ENUM, enumDefinition = RoleDefinition::class, default = DefaultConstants.ENUM_DEFAULT),
                Property(name = "char", type = CHAR, details = "1", default = DefaultConstants.CHAR_DEFAULT.toString()),
                Property(name = "varchar", type = VARCHAR, details = "26", default = DefaultConstants.VARCHAR_DEFAULT),
                Property(name = "binary", type = BINARY, details = "26", default = DefaultConstants.BINARY_DEFAULT),
                Property(name = "text", type = TEXT, default = DefaultConstants.TEXT_DEFAULT),
                Property(name = "boolean", type = BOOLEAN, default = DefaultConstants.BOOLEAN_DEFAULT.toString()),
                Property(name = "int", type = INT, default = DefaultConstants.INT_DEFAULT.toString()),
                Property(name = "long", type = LONG, default = DefaultConstants.LONG_DEFAULT.toString()),
                Property(name = "float", type = FLOAT, default = DefaultConstants.FLOAT_DEFAULT.toString()),
                Property(name = "numeric", type = NUMERIC, default = DefaultConstants.NUMERIC_DEFAULT),
                Property(name = "decimal", type = DECIMAL, default = DefaultConstants.DECIMAL_DEFAULT),
                Property(name = "double", type = DOUBLE, default = DefaultConstants.DOUBLE_DEFAULT.toString()),
                Property(name = "date", type = DATE, default = DefaultConstants.DATE_DEFAULT),
                Property(name = "datetime", type = DATETIME, default = DefaultConstants.DATETIME_DEFAULT),
                Property(name = "timestamp", type = TIMESTAMP, default = DefaultConstants.TIMESTAMP_DEFAULT),
            ]
        )
    ]
)
object TestDefaultDefinition
