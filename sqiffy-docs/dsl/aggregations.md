---
outline: deep
---

# Aggregations

An example is worth a thousand words:

```kotlin
val aggregatedData = database.select(UserTable)
    .slice(
        UserTable.count(),
        UserTable.name.count(),
        UserTable.id.sum(),
        UserTable.id.avg(),
        UserTable.id.min(),
        UserTable.id.max()
    )
    .groupBy(UserTable.name)
    .having { UserTable.id.count() greaterThan 0 }
    .map {
        mutableMapOf(
            "row_count" to it[UserTable.count()],
            "count" to it[UserTable.name.count()],
            "sum" to it[UserTable.id.sum()],
            "avg" to it[UserTable.id.avg()],
            "min" to it[UserTable.id.min()],
            "max" to it[UserTable.id.max()]
        )
    }
    .first()
```