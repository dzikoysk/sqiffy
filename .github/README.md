# Sqiffy [![CI](https://github.com/dzikoysk/sqiffy/actions/workflows/gradle.yml/badge.svg)](https://github.com/dzikoysk/sqiffy/actions/workflows/gradle.yml) ![Maven Central](https://img.shields.io/maven-central/v/com.dzikoysk.sqiffy/sqiffy)

**sqiffy** _(or just squiffy 🍹)_ - Experimental compound **SQ**L framework with type-safe DSL API generated at compile-time from scheme d**iff**.
It is dedicated for applications, plugins & libraries responsible for internal database management.

Table of contents:
1. [What it does?](#what-it-does)
2. [Supported](#supported)
3. [How to use](#how-to-use)
4. [Comparison with alternatives](#comparison-with-alternatives)

### What it does?

1. User defines versioned table definition using `@Defintion` annotation 
2. Sqiffy's annotation processor (KSP) at compile-time:
   1. Converts table definitions into versioned changelog, similar to [Liquibase](https://github.com/liquibase/liquibase)
   2. Generates up-to-date entity data classes for Kotlin with [KotlinPoet](https://github.com/square/kotlinpoet)
   3. Creates bindings for [Exposed (<ins>DSL</ins>)](https://github.com/JetBrains/Exposed) framework
   4. Validates schemes and bindings to eliminate typos and invalid operations
3. When application starts, you can run set of prepared versioned migrations against current database state

### Supported databases

| Database                                                                                                       | Support          | Notes                                                                                                                                                                                                                                                                                                             |
|----------------------------------------------------------------------------------------------------------------|------------------|-------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| [PostgreSQL](https://www.postgresql.org/), [Embedded PostgreSQL](https://github.com/zonkyio/embedded-postgres) | Full support     | Main target of the library.                                                                                                                                                                                                                                                                                       |
| [MariaDB](https://mariadb.org/), [MySQL](https://www.mysql.com/)                                               | Supported        | All operations should be supported, but some of the features might not be available.                                                                                                                                                                                                                              |
| [SQLite](https://www.sqlite.org/index.html)                                                                    | Work in progress | SQLite does not provide several crucial schema update queries & type system is flexible. Because of that, schema updates are based on top of the modifications applied to `sqlite_master`, but the stability of this solution is unknown. See [#2](https://github.com/dzikoysk/sqiffy/issues/2) for more details. |
| [H2 (MySQL mode)](http://www.h2database.com/html/features.html#compatibility)                                  | Unstable         | Such as SQLite, H2 implements SQL standard on their own & some of the compatibility features are just a fake mocks. In most cases, it's just better to use other databases (or their embedded variants).                                                                                                          |

### How to use

Gradle _(kts)_:

```kotlin
plugins {
    id("com.google.devtools.ksp") version "1.9.22-1.0.17" // for Kotlin 1.9.22
}

dependencies {
    val sqiffy = "1.0.0-alpha.64"
    ksp("com.dzikoysk.sqiffy:sqiffy-symbol-processor:$sqiffy") // annotation processor
    implementation("com.dzikoysk.sqiffy:sqiffy:$sqiffy") // core library & implementation
}
```

Describe your table using versioned definitions:

```kotlin
object UserAndGuildScenarioVersions {
    const val V_1_0_0 = "1.0.0"
    const val V_1_0_1 = "1.0.1"
    const val V_1_0_2 = "1.0.2"
}

@EnumDefinition(name = "role", mappedTo = "Role", [
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
        name = "users_table",
        properties = [
            Property(name = "id", type = SERIAL),
            Property(name = "uuid", type = UUID_TYPE),
            Property(name = "name", type = VARCHAR, details = "12"),
            Property(name = "role", type = ENUM, enumDefinition = RoleDefinition::class)
        ],
        constraints = [
            Constraint(type = PRIMARY_KEY, name = "pk_id", on = ["id"]),
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
])
object UserDefinition

@Definition([
    DefinitionVersion(
        version = V_1_0_0,
        name = "guilds_table",
        properties = [
            Property(name = "id", type = SERIAL),
            Property(name = "name", type = VARCHAR, details = "24"),
            Property(name = "owner", type = INT)
        ],
        constraints = [
            Constraint(type = FOREIGN_KEY, on = ["id"], name = "fk_id", referenced = UserDefinition::class, references = "id")
        ]
    ),
    DefinitionVersion(
        version = V_1_0_1,
        constraints = [
            Constraint(REMOVE_CONSTRAINT, type = FOREIGN_KEY, name = "fk_id")
        ]
    ),
    DefinitionVersion(
        version = V_1_0_2,
        constraints = [
            Constraint(type = FOREIGN_KEY, on = ["id"], name = "fk_id", referenced = UserDefinition::class, references = "id")
        ]
    )
])
object GuildDefinition
```

Build your project, so KSP can generate classes on top of the specified changelog. 
In this case it'll generate:

* `User`, `Guild` data class
* `UnidentifiedUser`, `UnidentifiedGuild` data class without autogenerated keys (like e.g. serial id)
* `UserTableNames`, `GuildTableNames` object with table & column names
* `Role` enum that is based on linked in scheme `@EnumDefinition`
* `UserTable`, `GuildTable` implementation of `Table` object for built-in DSL
* SQL migrations between each version

Then, you can simply connect to the database & run migrations:

```kotlin
this.database = Sqiffy.createDatabase(
    dataSource = createDataSource(),
    logger = Slf4JSqiffyLogger(LoggerFactory.getLogger(SqiffyDatabase::class.java))
)

val changeLog = database.generateChangeLog(UserDefinition::class, GuildDefinition::class)
database.runMigrations(changeLog = changeLog)

// [..] use database

database.close()
```

You can also execute queries using generated DSL:

```kotlin
val userToInsert = UnidentifiedUser(
    name = "Panda",
    displayName = "Only Panda",
    uuid = UUID.randomUUID(),
    role = Role.MODERATOR
)

val insertedUserWithDsl = database
    .insert(UserTable) {
        it[UserTable.uuid] = userToInsert.uuid
        it[UserTable.name] = userToInsert.name
        it[UserTable.displayName] = userToInsert.displayName
        it[UserTable.role] = userToInsert.role
    }
    .map { userToInsert.withId(id = it[UserTable.id]) }
    .first()

val guildToInsert = UnidentifiedGuild(
    name = "MONKE",
    owner = insertedUserWithDsl.id
)

val insertedGuild = database
    .insert(GuildTable) {
        it[GuildTable.name] = guildToInsert.name
        it[GuildTable.owner] = guildToInsert.owner
    }
    .map { guildToInsert.withId(id = it[GuildTable.id]) }
    .first()

println("Inserted user: $insertedUserWithDsl")

val userFromDatabaseUsingDsl = database.select(UserTable)
    .where { UserTable.uuid eq insertedUserWithDsl.uuid }
    .map {
        User(
            id = it[UserTable.id],
            name = it[UserTable.name],
            uuid = it[UserTable.uuid],
            displayName = it[UserTable.displayName],
            role = it[UserTable.role]
        )
    }
    .firstOrNull()

println("Loaded user: $userFromDatabaseUsingDsl")

val joinedData = database.select(UserTable)
    .join(INNER, UserTable.id, GuildTable.owner)
    .where { GuildTable.owner eq insertedGuild.owner }
    .map { it[UserTable.name] to it[GuildTable.name] }
    .first()

println(joinedData)
```

Or you can use generated names to execute manually, using e.g. JDBI:

```kotlin
val userFromDatabaseUsingRawJdbi = database.getJdbi().withHandle<User, Exception> { handle ->
    handle
        .select(multiline("""
            SELECT *
            FROM "${UserTableNames.TABLE}" 
            WHERE "${UserTableNames.NAME}" = :nameToMatch
        """))
        .bind("nameToMatch", "Panda")
        .mapTo<User>()
        .firstOrNull()
}

println("Loaded user: $userFromDatabaseUsingRawJdbi")
```

### Comparison with alternatives

The comparison shows differences between multiple approaches to database management,
there's no "best" approach, it's all about your preferences and needs. 
Sqiffy combines some known mechanisms to address issues of other approaches within the ecosystem of bundled applications shared among multiple users.

<table>
    <tr>
        <th>Approach</th>
        <th>Easy to use</th>
        <th>Control over the schema</th>
        <th>One source of truth</th>
        <th>Multiple dialects</th>
        <th>Auto-migrations</th>
        <th>Type-safe</th>
        <th>Compile-time validation</th>
        <th>DSL</th>
    </tr>
    <tr>
        <td>
            <b>Raw</b>
            <p>
                You want to avoid complex libraries and use raw SQL.
                Because of that, it increases amount of code you have to write, 
                and it's error-prone.
            </p>
        </td>
        <td>✗</td>
        <td>✓</td>
        <td>✗</td>
        <td>✗</td>
        <td>✗</td>
        <td>✗</td>
        <td>✗</td>
        <td>✗</td>
    </tr>
    <tr>
        <td>
            <b>SQL wrapper</b>
            <p>
                Libraries like JDBI simplifies interaction with raw SQL and entities, 
                but there's still much to do around it manually.
            </p>
        </td>
        <td>✗</td>
        <td>✓</td>
        <td>✗</td>
        <td>✗</td>
        <td>✗</td>
        <td>✗</td>
        <td>✗</td>
        <td>½</td>
    </tr>
    <tr>
        <td>
            <b>ORM</b>
            <p>
                ORM libraries promise to handle all the database stuff for you,
                but you're party losing control over the implementation and it may turn against you.
            </p>
        </td>
        <td>✓</td>
        <td>✗</td>
        <td>✓</td>
        <td>✓</td>
        <td>½</td>
        <td>½</td>
        <td>✗</td>
        <td>½</td>
    </tr>
    <tr>
        <td>
            <b>DSL</b>
            <p>
                Libraries like Exposed DSL provides very convenient type-safe API and basic scheme generators, 
                but you partly lose control over the schema and you may encounter several issues with their API that doesn't cover all your needs.
            </p>
        </td>
        <td>✓</td>
        <td>✓</td>
        <td>✓</td>
        <td>½</td>
        <td>½</td>
        <td>½</td>
        <td>½</td>
        <td>✓</td>
    </tr>
    <tr>
        <td>
            <b>DSL/ORM + Liquibase/Flyway</b>
            <p>
                Migrations are a must-have for any database management system,
                but it's not easy to implement them in a type-safe way and implement them for multiple dialects & users.
            </p>
        </td>
        <td>½</td>
        <td>✓</td>
        <td>✗</td>
        <td>½</td>
        <td>✓</td>
        <td>½</td>
        <td>✗</td>
        <td>N/A</td>
    </tr>
    <tr>
        <td>
            <b>JOOQ</b>
            <p>
                JOOQ defines a new category,
                and while it's pretty good escape from regular DSL and uncontrolled ORMs, 
                it targets enterprise products with an existing databases controlled by 3rd party sources, so
                it's not that good for any kind of bundled application.
            </p>
        </td>
        <td>✓</td>
        <td>N/A</td>
        <td>✓</td>
        <td>½</td>
        <td>✓</td>
        <td>✓</td>
        <td>✓</td>
        <td>✓</td>
    </tr>
    <tr>
        <td>
            <b>Sqiffy</b>
            <p>
                Combines several features mentioned above as opt-in and handles bundled database schema changelog.
            </p>
        </td>
        <td>✓</td>
        <td>✓</td>
        <td>✓</td>
        <td>✓</td>
        <td>✓</td>
        <td>✓</td>
        <td>✓</td>
        <td>✓</td>
    </tr>
</table>

```
✓ - Yes
✗ - No
½ - Partially or not exactly matching our target (bundled apps with swappable database & dialects)
N/A - Not applicable or given library is not responsible for this feature
```
