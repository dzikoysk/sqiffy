---
outline: deep
---

# Enums

If your database scheme uses enums, and there's a chance you'll need to update them, you should also start versioning them.

### Versioned enums

Sqiffy allows you to define enums in a similar way to entities and DTOs, via `@EnumDefinition` annotation:

```kotlin
@EnumDefinition(name = "user_role", mappedTo = "com.example.user.api.UserRole", [
    EnumVersion(
        version = V_0_0_0,
        operation = ADD_VALUES,
        values = ["USER", "ADMIN"]
    ),
])
internal object UserRoleDefinition
```

To use such enum later on in your database schema, you have to reference it via `UserRoleDefinition` object:

```kotlin
@Definition([
    DefinitionVersion(
        version = V_0_0_0,
        name = "users",
        properties = [
            Property(name = "id", type = SERIAL),
            Property(name = "role", type = ENUM, enumDefinition = UserRoleDefinition::class),
        ]
    )
])
object UserDefinition
```

And that's it! The `com.example.user.api.UserRole` class will be generated for you, and you can use it in your code.

### Raw enums

If you don't want to version your enums, you can use raw enums in your database schema:

```kotlin
@RawEnum("user_role")
enum class UserRole {
    ADMIN, 
    USER
}
```

And then reference it in your entity definition:

```kotlin
@Definition([
    DefinitionVersion(
        version = V_0_0_0,
        name = "users",
        properties = [
            Property(name = "id", type = SERIAL),
            Property(name = "role", type = ENUM, enumDefinition = UserRole::class),
        ]
    )
])
object UserDefinition
```

It might be a convinient solution, if you'll decide to version you database schema using some other tool, so when you're using Sqiffy purely for DSL.


> **Warning**: Keep in mind this enum won't be versioned, so it also won't be generated for you. You'll have to create it manually in your database.