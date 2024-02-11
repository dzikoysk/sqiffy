---
outline: deep
---

# Migrations

Migrations are a way to manage changes to the database schema over time.
Currently, Sqiffy supports two ways of managing schema changes:

1. **Sqiffy migrations** - apply schema changes to the database automatically, without the need to write SQL scripts
2. **Liquibase** - Sqiffy can also generate [Liquibase](https://www.liquibase.org/) files

It's fully optional, so if you don't want to manage schema changes in Sqiffy, you can use it only for generating type-safe Kotlin DSL API.
Each migration requires a changelog, which is a list of changes between each version of the schema.
To generate a changelog, you can use the `generateChangeLog` method from the `SqiffyDatabase` instance:

```kotlin
val changeLog = database.generateChangeLog(
  tables = listOf(
    UserDefinition::class, 
    // other table definitions
  )
)
```

To run a migration, you need to create a migrator and apply it to the database:

```kotlin
database.runMigrations(migrator)
```

See the following sections for more details about each migrator.

## Sqiffy migrator

Sqiffy migrator is a simple built-in tool that allows you to run a set of required changes to the database.
You can also adjust table name used to store data about past migrations and run callbacks before and after each version is applied:

```kotlin
val migrator = SqiffyMigrator(
  changeLog = changeLog // required
  metadataTable = SqiffyMetadataTable(name = "sqiffy_metadata") // optional
  versionCallbacks = VersionCallbacks() // optional
      .before(V_1_0_0) { /* do something before applying 1.0.0 changes */ }
      .after(V_1_0_0) { /* do something after applying 1.0.0 changes */ }
      .before(V_1_0_1) { /* do something before applying 1.0.1 changes */ }
      .after(V_1_0_1) { /* do something after applying 1.0.1 changes */ }
)
```

## Liquibase migrator

Liquibase is a popular tool for managing database schema changes.
To enable Liquibase support in Sqiffy, you need to define changelog provider in `@ChangelogDefinition` annotation.
Usually, it's a good idea to put it above the object with versions of your schema:

```kotlin
@ChangelogDefinition(
    projectName = "Project",
    dialect = POSTGRESQL,
    provider = LIQUIBASE
)
object ProjectVersion {
    const val V_1_0_0 = "1.0.0"
    const val V_1_0_1 = "1.0.1"
    const val V_1_0_2 = "1.0.2"
}
```

Because we're generating `*.sql` files for Liquibase, you also need to define your target SQL dialect. 
Changelog files are generated with DSL, during the compilation process, so let's run KSP task to generate them:

```bash
$ gradle kspKotlin
```

To run generated Liquibase files, you can use the `LiquibaseMigrator`:

```kotlin
val migrator = LiquibaseMigrator(
  changelogFile = "/liquibase/changelog-master.xml" // optional
)
```

The `LiquibaseMigrator` implementation uses the `liquibase` library under the hood.
If you'd like to run generated Liquibase files on your own, you can find them in the `build/resources/main/liquibase` directory.