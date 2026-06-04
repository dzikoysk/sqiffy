---
outline: deep
---

# Migrations

Migrations are a way to manage changes to the database schema over time.
Currently, Sqiffy supports three ways of managing schema changes:

1. **Sqiffy migrations** - apply schema changes to the database automatically, without the need to write SQL scripts
2. **File migrations** - apply your own `*.sql` scripts, listed in an index file, with no Liquibase dependency
3. **Liquibase** - Sqiffy can also generate [Liquibase](https://www.liquibase.org/) files

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

## File migrator

The file migrator applies your own `*.sql` scripts and tracks which ones have already run, without
pulling in Liquibase. Unlike the Sqiffy migrator, it doesn't need a generated changelog - you write
the SQL yourself, which is handy when you want full control over the statements (functions, custom
types, data backfills) or you're moving an existing project off Liquibase.

Instead of scanning a directory (unreliable inside shaded JARs and non-deterministic in order), the
migrator reads an **index file**: a plain-text manifest that lists the scripts in the exact order they
should be applied. Put it on the classpath, e.g. `src/main/resources/database/changelog.index`:

```
# One migration script per line, applied top to bottom.
# Blank lines and lines starting with '#' are ignored.

1.0.0/001-create-users.sql
1.0.0/002-add-email.sql
1.0.0/003-add-counter-function.sql
```

Paths are resolved relative to the index file's own location (so the entries above resolve to
`database/1.0.0/...`). Each listed file is one migration, and a file may contain multiple statements
(on PostgreSQL the whole body runs at once, so dollar-quoted PL/pgSQL functions work as written). Every
script runs in its own transaction (falling back to autocommit for statements a database refuses to run
inside one, such as `ALTER TYPE ... ADD VALUE` on older PostgreSQL) and is recorded - with a SHA-256
checksum - in the same `sqiffy_metadata` table used by the Sqiffy migrator. Re-runs are idempotent:
already-applied scripts are skipped.

To run it, point the migrator at the index file:

```kotlin
database.runMigrations(FileMigrator("database/changelog.index"))
```

The migrator accepts a few optional settings:

```kotlin
val migrator = FileMigrator(
  indexPath = "database/changelog.index",            // required
  checksumPolicy = ChecksumPolicy.FAIL,              // FAIL (default) | WARN | IGNORE on content drift
  liquibaseChangelogTable = "databasechangelog",     // adopt existing Liquibase state (default on); null to disable
  metadataTable = SqiffyMetadataTable(),             // optional, shared with SqiffyMigrator
)
```

`checksumPolicy` controls what happens when an already-applied script's content later changes:
`FAIL` aborts the migration (the safe default), `WARN` logs and continues, `IGNORE` stays silent.

### Migrating from Liquibase

If your database is already managed by Liquibase, the file migrator can adopt it with no manual step.
On the **first** run (when `sqiffy_metadata` holds no file changesets yet) it looks for the existing
`databasechangelog` table and, for every script whose path matches an already-applied Liquibase
`filename`, records it as applied **without re-executing it**. Scripts Liquibase never saw are applied
normally. After that first run the `databasechangelog` table is left untouched and ignored.

So a typical Liquibase cutover is:

1. Add a `changelog.index` listing your existing `*.sql` files in order (the `--changeset`/`--liquibase`
   header comments in those files are ignored, so the files don't need to change).
2. Swap `LiquibaseMigrator` for `FileMigrator("database/changelog.index")`.
3. Drop the `org.liquibase:liquibase-core` dependency.

The import is on by default. Set `liquibaseChangelogTable = null` to disable it once the cutover is
done, or point it at a non-default tracking table name.

::: tip Notes
- Multi-statement scripts work on every dialect: PostgreSQL runs the whole body in one go (so
  dollar-quoted PL/pgSQL functions execute as written), while MySQL/SQLite split each script into
  individual statements. PostgreSQL-only syntax (dollar quoting, `ALTER TYPE`) is, of course, still PostgreSQL-only.
- DDL such as `ALTER TYPE ... ADD VALUE` inside a transaction needs PostgreSQL 12+; the migrator
  automatically retries a script outside a transaction if the database reports it can't run in one.
- The migrator does not take a distributed lock, so run migrations once at startup with a single
  instance migrating before the others come up (e.g. a rolling release).
:::

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