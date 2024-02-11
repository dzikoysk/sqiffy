---
outline: deep
---

# Dialects

Sqiffy supports dialect-specific queries. This allows you to write queries that are specific to the database you are using.

## PostgreSQL

* Upsert:

```kotlin
val (id, displayName) = 
    postgresDatabase
        .upsert(UserTable)
        .insert {
            it[UserTable.name] = "panda"
            it[UserTable.displayName] = "Panda"
        }
        .update {
            it[UserTable.displayName] = "Giant Panda"
        }
        .execute { it[UserTable.id] to it[UserPanda.displayName] }
        .first()
```