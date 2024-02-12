---
outline: deep
aside: false
---

# Conditions

Examples of conditions that can be used in queries:

| Function | Example |
| --- | --- |
| Equals | `UserTable.id eq 123456` |
| Not equals | `UserTable.name notEq "Panda` |
| Left greater than right | `UserTable.id greaterThan 1000` |
| Left greater or equals right | `UserTable.id greaterThanOrEq 1000` |
| Left less than right | `UserTable.id lessThan 1000` |
| Left less or equals right | `UserTable.id lessThanOrEq 1000` |
| Like | `UserTable.name like "G%O%N%E"` |
| Not like | `UserTable.name notLike "G%O%N%E"` |
| Between | `UserTable.createdAt between (now().minusMinutes(1) and now().plusMinutes(1))` |
| Not Between  | `UserTable.createdAt notBetween (now().minusMinutes(1) and now().plusMinutes(1))` |

If you want to combine multiple conditions, you can use the `and` and `or` functions:

```kotlin
where {
    or(
        and(
            UserTable.id notEq 1,
            UserTable.name like "A%",
        ),
        and(
            UserTable.id notEq 2,
            UserTable.name like "B%",
        )
    )
}
```