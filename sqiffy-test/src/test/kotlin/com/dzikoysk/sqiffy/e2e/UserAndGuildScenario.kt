package com.dzikoysk.sqiffy.e2e

import com.dzikoysk.sqiffy.Dialect.POSTGRESQL
import com.dzikoysk.sqiffy.definition.ChangelogDefinition
import com.dzikoysk.sqiffy.definition.ChangelogProvider.LIQUIBASE
import com.dzikoysk.sqiffy.definition.Constraint
import com.dzikoysk.sqiffy.definition.ConstraintDefinitionType.REMOVE_CONSTRAINT
import com.dzikoysk.sqiffy.definition.ConstraintType.FOREIGN_KEY
import com.dzikoysk.sqiffy.definition.ConstraintType.PRIMARY_KEY
import com.dzikoysk.sqiffy.definition.DataType.DATETIME
import com.dzikoysk.sqiffy.definition.DataType.ENUM
import com.dzikoysk.sqiffy.definition.DataType.FLOAT
import com.dzikoysk.sqiffy.definition.DataType.INT
import com.dzikoysk.sqiffy.definition.DataType.SERIAL
import com.dzikoysk.sqiffy.definition.DataType.UUID_TYPE
import com.dzikoysk.sqiffy.definition.DataType.VARCHAR
import com.dzikoysk.sqiffy.definition.Definition
import com.dzikoysk.sqiffy.definition.DefinitionVersion
import com.dzikoysk.sqiffy.definition.DslDefinition
import com.dzikoysk.sqiffy.definition.DtoDefinition
import com.dzikoysk.sqiffy.definition.EnumDefinition
import com.dzikoysk.sqiffy.definition.EnumOperation.ADD_VALUES
import com.dzikoysk.sqiffy.definition.EnumVersion
import com.dzikoysk.sqiffy.definition.Index
import com.dzikoysk.sqiffy.definition.IndexDefinitionOperation.REMOVE_INDEX
import com.dzikoysk.sqiffy.definition.IndexType.INDEX
import com.dzikoysk.sqiffy.definition.IndexType.UNIQUE_INDEX
import com.dzikoysk.sqiffy.definition.NamingStrategy.CAMEL_CASE
import com.dzikoysk.sqiffy.definition.Property
import com.dzikoysk.sqiffy.definition.PropertyDefinitionOperation.ADD
import com.dzikoysk.sqiffy.definition.PropertyDefinitionOperation.RENAME
import com.dzikoysk.sqiffy.definition.PropertyDefinitionOperation.RETYPE
import com.dzikoysk.sqiffy.definition.RawEnum
import com.dzikoysk.sqiffy.definition.Variant
import com.dzikoysk.sqiffy.e2e.UserAndGuildScenarioVersions.V_1_0_0
import com.dzikoysk.sqiffy.e2e.UserAndGuildScenarioVersions.V_1_0_1
import com.dzikoysk.sqiffy.e2e.UserAndGuildScenarioVersions.V_1_0_2
import com.dzikoysk.sqiffy.infra.UserTableNames
import java.io.Serializable

@DslDefinition(
    namingStrategy = CAMEL_CASE
)
@ChangelogDefinition(
    projectName = "GuildService",
    dialect = POSTGRESQL,
    provider = LIQUIBASE
)
object UserAndGuildScenarioVersions {
    const val V_1_0_0 = "1.0.0"
    const val V_1_0_1 = "1.0.1"
    const val V_1_0_2 = "1.0.2"
}

typealias UserId = Int

@Definition(
    domainPackage = "com.dzikoysk.sqiffy.domain",
    infrastructurePackage = "com.dzikoysk.sqiffy.infra",
    apiPackage = "com.dzikoysk.sqiffy.api",
    versions = [
        DefinitionVersion(
            version = V_1_0_0,
            name = "users",
            properties = [
                Property(name = "id", type = SERIAL, mappedTo = UserId::class),
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
            implements = [ Serializable::class ]
        ),
    ]
)
object UserDtoDefinition

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

@Definition([
    DefinitionVersion(
        version = V_1_0_0,
        name = "guilds",
        properties = [
            Property(name = "id", type = SERIAL),
            Property(name = "name", type = VARCHAR, details = "24"),
            Property(name = "owner", type = INT),
            Property(name = "created_at", type = DATETIME),
            Property(name = "type", type = ENUM, enumDefinition = GuildType::class, default = "DEFAULT"),
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

@RawEnum
enum class GuildType {
    DEFAULT
}