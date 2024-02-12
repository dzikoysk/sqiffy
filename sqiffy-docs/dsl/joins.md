---
outline: deep
aside: false
---

# Joins

You can join tables using the `join` function. 
The first parameter is the type of join, the second parameter is the column to join, and the third parameter is the column to join with. Take a look at the following example:

```kotlin
val joinedData = database.select(UserTable)
    .join(INNER, UserTable.id, GuildTable.owner)
    .slice(UserTable.name, GuildTable.name)
    .where { GuildTable.owner eq insertedGuild.owner }
    .map { it[UserTable.name] to it[GuildTable.name] }
    .first()
```

With the `slice` function, you can select the columns you want to retrieve from the query (so in the `map` function).