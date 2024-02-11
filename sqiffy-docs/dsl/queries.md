---
outline: deep
aside: false
---

# Queries

## Select

Standard select query:

```kotlin
val userFromDatabase = database.select(UserTable)
    .where { UserTable.uuid eq insertedUser.uuid }
    .map { it.toUser() }
    .first()
```

Other parameters:

| Parameter | Example | Description |
| --- | --- | --- |
| Distinct | `distinct()` | Only return distinct rows |
| Limit | `limit(10)` | Limit the number of rows returned |
| Limit with offset | `limit(10, offset = 5)` | Limit the number of rows returned with an offset |
| Order by | `orderBy(UserTable.name to SortOrder.ASC)` | Order the results by a column 
| Group by | `groupBy(UserTable.name)` | Group the results by a column |
| Having | `having { UserTable.name eq "Panda" }` | Filter the results after grouping |
| Join | `innerJoin(INNER/LEFT/RIGHT/FULL, GuildTable.owner, UserTable.id)` | Join two tables |
| Slice | `slice(UserTable.name, UserTable.age)` | Only return specific columns |

## Insert

Standard insert query:

```kotlin
val insertedUser = database
    .insert(UserTable)
    .values(UnidentifiedUser(name = "Panda", age = 20))
    .map { userToInsert.withId(id = it[UserTable.id]) }
    .first()
```

You can also insert individual rows:

```kotlin
val insertedUser = database
    .insert(UserTable)
    .values {
        it[UserTable.name] = "Panda"
        it[UserTable.age] = 20
    }
    .map { userToInsert.withId(id = it[UserTable.id]) }
    .first()
```


## Update

Standard update query:

```kotlin
val updatedRecords = database
    .update(UserTable) {
        it[UserTable.name] = "Giant Panda"
        it[UserTable.age] = UserTable.age * 2
    }
    .where { UserTable.id eq insertedUser.id }
    .execute()
```

## Delete

Standard delete query:

```kotlin
database
    .delete(UserTable)
    .where { UserTable.id eq id }
    .execute()
```