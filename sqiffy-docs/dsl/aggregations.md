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
        UserTable.name.countDistinct(),
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
            "count_distinct" to it[UserTable.name.countDistinct()],
            "sum" to it[UserTable.id.sum()],
            "avg" to it[UserTable.id.avg()],
            "min" to it[UserTable.id.min()],
            "max" to it[UserTable.id.max()]
        )
    }
    .first()
```

## Available aggregations

| Function | SQL | Description |
|---|---|---|
| `Table.count()` | `COUNT(*)` | Count all rows |
| `Column.count()` | `COUNT(column)` | Count non-null values |
| `Column.countDistinct()` | `COUNT(DISTINCT column)` | Count distinct non-null values |
| `Column.sum()` | `SUM(column)` | Sum of numeric values |
| `Column.avg()` | `AVG(column)` | Average of numeric values |
| `Column.min()` | `MIN(column)` | Minimum value |
| `Column.max()` | `MAX(column)` | Maximum value |