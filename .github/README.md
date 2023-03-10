# Sqiffy [![CI](https://github.com/dzikoysk/sqiffy/actions/workflows/gradle.yml/badge.svg)](https://github.com/dzikoysk/sqiffy/actions/workflows/gradle.yml) ![Maven Central](https://img.shields.io/maven-central/v/com.dzikoysk.sqiffy/sqiffy-specification)

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

### Supported

* [x] MySQL/MariaDB
* [x] H2 (MySQL Mode)
* [x] PostgreSQL
* [ ] SQLite

### How to use

Gradle _(kts)_:

```kotlin
plugins {
    id("com.google.devtools.ksp") version "1.8.0-1.0.8"
}

dependencies {
    val sqiffy = "1.0.0-alpha.6"
    ksp("com.dzikoysk.sqiffy:sqiffy-symbol-processor:$sqiffy") // annotation processor
    implementation("com.dzikoysk.sqiffy:sqiffy-specification:$sqiffy") // annotations & compile-time api
    implementation("com.dzikoysk.sqiffy:sqiffy-library:$sqiffy") // core library & implementation
}
```

Describe your table using versioned definitions:

```kotlin
object Versions {
    const val V_1_0_0 = "1.0.0"
    const val V_1_0_1 = "1.0.1"
    const val V_1_0_2 = "1.0.2"
}

@Definition([
    DefinitionVersion(
        version = V_1_0_0,
        name = "users_table",
        properties = [
            Property(name = "id", type = INT, autoincrement = true),
            Property(name = "uuid", type = UUID_BINARY),
            Property(name = "name", type = VARCHAR, details = "12"),
        ],
        constraints = [
            Constraint(type = PRIMARY_KEY, name = "pk_id", on = "id"),
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
            Property(name = "display_name", type = VARCHAR, details = "48", nullable = true)
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
            Property(name = "id", type = INT, autoincrement = true),
            Property(name = "owner", type = INT)
        ],
        constraints = [
            Constraint(type = FOREIGN_KEY, on = "id", name = "fk_id", referenced = UserDefinition::class, references = "id")
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
            Constraint(type = FOREIGN_KEY, on = "id", name = "fk_id", referenced = UserDefinition::class, references = "id")
        ]
    )
])
object GuildDefinition
```

Build your project, so KSP can generate classes on top of the specified changelog. 
In this case it'll generate:

* `User`, `Guild` data class
* `UserTable`, `GuildTable` implementation of Exposed's `Table` object
* SQL migrations between each version

Then, you can simply connect to the database, run migrations & use DSL:

```kotlin
val sqiffy = Sqiffy(
    dataSource = createHikariDataSource(
        driver = "org.h2.Driver",
        url = "jdbc:h2:${createTestDatabaseFile("test-database").absolutePathString()};MODE=MYSQL",
        threadPool = 1
    ),
    logger = Slf4JSqiffyLogger(LoggerFactory.getLogger(Sqiffy::class.java))
)

// read current version from `sqiffy_scheme_version` table and run missing migrations
sqiffy.transaction {
    val changeLog = sqiffy.generateChangeLog(UserDefinition::class, GuildDefinition::class)
    sqiffy.runMigrations(changeLog)
}

// save entity with generated exposed dsl bindings
sqiffy.transaction {
    // generated by sqiffy entity with named fields
    val user = User(
        id = 69,
        name = "Panda",
        uuid = UUID.randomUUID(),
        displayName = "Sadge"
    )

    UserTable.insert {
        it[UserTable.id] = user.id
        it[UserTable.name] = user.name
        it[UserTable.uuid] = user.uuid
        it[UserTable.displayName] = user.displayName
    }
}

// read entity from database with generated exposed dsl bindings
val userFromDatabase = sqiffy.transaction {
    UserTable.select { UserTable.name eq "Panda" }
        .first()
        .let {
            User(
                id = it[UserTable.id],
                name = it[UserTable.name],
                uuid = it[UserTable.uuid],
                displayName = it[UserTable.displayName]
            )
        }
}

println(userFromDatabase)
sqiffy.close()
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
