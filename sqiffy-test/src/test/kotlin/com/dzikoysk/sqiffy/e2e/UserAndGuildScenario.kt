package com.dzikoysk.sqiffy.e2e

import com.dzikoysk.sqiffy.definition.Constraint
import com.dzikoysk.sqiffy.definition.ConstraintDefinitionType.REMOVE_CONSTRAINT
import com.dzikoysk.sqiffy.definition.ConstraintType
import com.dzikoysk.sqiffy.definition.ConstraintType.FOREIGN_KEY
import com.dzikoysk.sqiffy.definition.ConstraintType.PRIMARY_KEY
import com.dzikoysk.sqiffy.definition.DataType.BINARY
import com.dzikoysk.sqiffy.definition.DataType.BOOLEAN
import com.dzikoysk.sqiffy.definition.DataType.CHAR
import com.dzikoysk.sqiffy.definition.DataType.DATE
import com.dzikoysk.sqiffy.definition.DataType.DATETIME
import com.dzikoysk.sqiffy.definition.DataType.DOUBLE
import com.dzikoysk.sqiffy.definition.DataType.ENUM
import com.dzikoysk.sqiffy.definition.DataType.FLOAT
import com.dzikoysk.sqiffy.definition.DataType.INT
import com.dzikoysk.sqiffy.definition.DataType.LONG
import com.dzikoysk.sqiffy.definition.DataType.SERIAL
import com.dzikoysk.sqiffy.definition.DataType.TEXT
import com.dzikoysk.sqiffy.definition.DataType.TIMESTAMP
import com.dzikoysk.sqiffy.definition.DataType.UUID_TYPE
import com.dzikoysk.sqiffy.definition.DataType.VARCHAR
import com.dzikoysk.sqiffy.definition.Definition
import com.dzikoysk.sqiffy.definition.DefinitionVersion
import com.dzikoysk.sqiffy.definition.DtoDefinition
import com.dzikoysk.sqiffy.definition.EnumDefinition
import com.dzikoysk.sqiffy.definition.EnumOperation.ADD_VALUES
import com.dzikoysk.sqiffy.definition.EnumVersion
import com.dzikoysk.sqiffy.definition.Index
import com.dzikoysk.sqiffy.definition.IndexDefinitionOperation.REMOVE_INDEX
import com.dzikoysk.sqiffy.definition.IndexType
import com.dzikoysk.sqiffy.definition.IndexType.INDEX
import com.dzikoysk.sqiffy.definition.IndexType.UNIQUE_INDEX
import com.dzikoysk.sqiffy.definition.Property
import com.dzikoysk.sqiffy.definition.PropertyDefinitionOperation.ADD
import com.dzikoysk.sqiffy.definition.PropertyDefinitionOperation.RENAME
import com.dzikoysk.sqiffy.definition.PropertyDefinitionOperation.RETYPE
import com.dzikoysk.sqiffy.definition.Variant
import com.dzikoysk.sqiffy.e2e.UserAndGuildScenarioVersions.V_1_0_0
import com.dzikoysk.sqiffy.e2e.UserAndGuildScenarioVersions.V_1_0_1
import com.dzikoysk.sqiffy.e2e.UserAndGuildScenarioVersions.V_1_0_2
import com.dzikoysk.sqiffy.infra.UserTableNames
import java.io.Serializable
import java.util.UUID

object UserAndGuildScenarioVersions {
    const val V_1_0_0 = "1.0.0"
    const val V_1_0_1 = "1.0.1"
    const val V_1_0_2 = "1.0.2"
}

@EnumDefinition(name = "role", mappedTo = "com.dzikoysk.sqiffy.api.Role", [
    EnumVersion(
        version = V_1_0_0,
        operation = ADD_VALUES,
        values = ["ADMIN", "USER"]
    ),
    EnumVersion(
        version = V_1_0_1,
        operation = ADD_VALUES,
        values = ["MODERATOR", "SPECTATOR"]
    )
])
object RoleDefinition

@Definition(
    domainPackage = "com.dzikoysk.sqiffy.domain",
    infrastructurePackage = "com.dzikoysk.sqiffy.infra",
    apiPackage = "com.dzikoysk.sqiffy.api",
    versions = [
        DefinitionVersion(
            version = V_1_0_0,
            name = "users_table",
            properties = [
                Property(name = "id", type = SERIAL),
                Property(name = "uuid", type = UUID_TYPE),
                Property(name = "name", type = VARCHAR, details = "12"),
                Property(name = "wallet", type = FLOAT, default = "0.0"),
                Property(name = "role", type = ENUM, enumDefinition = RoleDefinition::class, default = "USER"),
            ],
            constraints = [
                Constraint(type = PRIMARY_KEY, name = "pk_id", on =["id"]),
            ],
            indices = [
                Index(type = INDEX, name = "idx_id", columns = ["id"]),
                Index(type = UNIQUE_INDEX, name = "uq_name", columns = ["name"])
            ]
        ),
        DefinitionVersion(
            version = V_1_0_1,
            properties = [
                Property(operation = RETYPE, name = "name", type = VARCHAR, details = "24"),
                Property(operation = ADD, name = "display_name", type = VARCHAR, details = "48", nullable = true),
            ],
            indices = [
                Index(operation = REMOVE_INDEX, type = INDEX, name = "idx_id"),
                Index(type = INDEX, name = "idx_id", columns = ["id"])
            ]
        ),
        DefinitionVersion(
            version = V_1_0_2,
            properties = [
                Property(operation = RENAME, name = "display_name", rename = "displayName")
            ]
        )
    ]
)
object UserDefinition

@DtoDefinition(
    from = UserDefinition::class,
    variants = [
        Variant(
            name = "UserDto",
            properties = [ UserTableNames.NAME ],
            implements = [ Serializable::class ]
        ),
        Variant(
            name = "AllUserDto",
            allProperties = true,
            implements = [ Serializable::class ]
        ),
    ]
)
object UserDtoDefinition

@Definition([
    DefinitionVersion(
        version = V_1_0_0,
        name = "guilds_table",
        properties = [
            Property(name = "id", type = SERIAL),
            Property(name = "name", type = VARCHAR, details = "24"),
            Property(name = "owner", type = INT),
            Property(name = "createdAt", type = DATETIME)
        ],
        constraints = [
            Constraint(type = PRIMARY_KEY, name = "pk_guild_id", on = ["id"]),
            Constraint(type = FOREIGN_KEY, name = "fk_guild_owner", on = ["owner"], referenced = UserDefinition::class, references = "id")
        ]
    ),
    DefinitionVersion(
        version = V_1_0_1,
        constraints = [
            Constraint(REMOVE_CONSTRAINT, type = FOREIGN_KEY, name = "fk_guild_owner")
        ]
    ),
    DefinitionVersion(
        version = V_1_0_2,
        constraints = [
            Constraint(type = FOREIGN_KEY, name = "fk_guild_owner", on = ["owner"], referenced = UserDefinition::class, references = "id")
        ]
    )
])
object GuildDefinition

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
                Property(name = "double", type = DOUBLE, default = DefaultConstants.DOUBLE_DEFAULT.toString()),
                Property(name = "date", type = DATE, default = DefaultConstants.DATE_DEFAULT),
                Property(name = "datetime", type = DATETIME, default = DefaultConstants.DATETIME_DEFAULT),
                Property(name = "timestamp", type = TIMESTAMP, default = DefaultConstants.TIMESTAMP_DEFAULT),
            ]
        )
    ]
)
object TestDefaultDefinition

