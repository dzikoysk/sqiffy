# Sqiffy 

**sqiffy** _(or just squiffy 🍹)_ - Compound **SQ**L framework with type-safe DSL API generated at compile-time from scheme d**iff**.
It is dedicated for applications, plugins & libraries responsible for internal database management.

### What it does?

1. User defines versioned table definition using `@Defintion` annotation 
2. Sqiffy's annotation processor (KSP) at compile-time:
   1. Converts table definitions into versioned changelog, similar to [Liquibase](https://github.com/liquibase/liquibase)
   2. Generates up-to-date entity data classes for Kotlin with [KotlinPoet](https://github.com/square/kotlinpoet)
   3. Creates bindings for [Exposed (<ins>DSL</ins>)](https://github.com/JetBrains/Exposed) framework
3. When application starts, you can run set of prepared versioned migrations against current database state

### Supports

* MySQL/MariaDB
* H2 (MySQL Mode)

### How to use

Describe your table using versioned definitions:

```kotlin
@Definition([
    DefinitionVersion(
        version = "1.0.0",
        name = "users_table",
        properties = [
            Property(name = "id", type = INT, autoincrement = true),
            Property(name = "uuid", type = UUID_VARCHAR),
            Property(name = "name", type = VARCHAR, details = "16"),
        ],
        constraints = [
            Constraint(PRIMARY_KEY, on = "id"),
        ],
        indices = [
            Index(UNIQUE_INDEX, columns = ["name"])
        ]
    ),
    DefinitionVersion(
        version = "1.0.1",
        properties = [
            Property(name = "display_name", type = VARCHAR, details = "48", nullable = true)
        ]
    ),
    DefinitionVersion(
        version = "1.0.2",
        properties = [
            Property(RENAME, name = "display_name", rename = "displayName")
        ]
    )
])
object UserDefinition
```

Build your project, so KSP can generate classes on top of the specified changelog. 
In this case it'll generate:

* `User` data class
* `UserTable` implementation of Exposed's `Table` object

Then, you can simply connect to the database, run migrations & use DSL:

```kotlin
val dataSource = createDataSource(
    driver = "org.h2.Driver",
    url = "jdbc:h2:${createTestDatabaseFile("test-database").absolutePathString()};MODE=MYSQL",
    threadPool = 1
)

dataSource.toDatabaseConnection().use { databaseConnection ->
    transaction(databaseConnection.database) {
        val result = BaseSchemeGenerator().generateChangeLog(UserDefinition::class, GuildDefinition::class)
        result.runMigrations(databaseConnection.database)

        // generated entity
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

        val userFromDatabase = UserTable.select { UserTable.name eq "Panda" }
            .first()
            .let {
                User(
                    id = it[UserTable.id],
                    name = it[UserTable.name],
                    uuid = it[UserTable.uuid],
                    displayName = it[UserTable.displayName]
                )
            }

        println(userFromDatabase)
    }
}
```