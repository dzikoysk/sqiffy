---
outline: deep
---

# Definining a schema

Sqiffy allows you to define your database schema in Kotlin code. 
The schema is defined using annotations and is then used to generate a type-safe Kotlin DSL API for interacting with the database.

Benefits of using annotations to define the schema:
* Enforces static schema declaration
* Dialect-agnostic definition
* Reduces the risk of inaccuracies between type definitions and actual database schema
* Reduces code repetition

Let's create a simple schema for a user table:

```kotlin
@Definition([
    DefinitionVersion(
        version = "1.0.0",
        name = "users",
        properties = [
            Property(name = "id", type = SERIAL),
            Property(name = "name", type = VARCHAR, details = "12"),
        ]
    )
])
object UserDefinition
```

A database schema not only contains a set of properties, but also constraints and indices. 
To define them, you can use `constraints` & `indices` fields in `DefinitionVersion` annotation:

```kotlin
@Definition([
    DefinitionVersion(
        version = "1.0.0",
        name = "users",
        properties = [
            Property(name = "id", type = SERIAL),
            Property(name = "name", type = VARCHAR, details = "12"),
        ],
        constraints = [
            Constraint(type = PRIMARY_KEY, name = "pk_id", on = "id"),
        ],
        indices = [
            Index(type = UNIQUE_INDEX, name = "uq_name", columns = ["name"])
        ]
    )
])
object UserDefinition
```

Annotations are limited to constant values, so you can't use any expressions. 
Fortunately, you can `const val` variables to use some shared values:

```kotlin
const val V_1_0_0 = "1.0.0"

const val USER_NAME_MAX_LENGTH = 12

@Definition([
    DefinitionVersion(
        version = V_1_0_0,
        name = "users",
        properties = [
            Property(name = "id", type = SERIAL),
            Property(name = "name", type = VARCHAR, details = "$USER_NAME_MAX_LENGTH"),
        ],
        // ...
    )
])
object UserDefinition
```

That's all! In the next chapter you'll learn how to use the generated API to interact with the database.