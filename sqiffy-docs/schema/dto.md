---
outline: deep
---

# DTOs

> When we talk about DTOs, we are referring to Data Transfer Objects. 
> These are objects that are used to transfer data between layers of the application to encapsulate data and send it from one part of the application to another.

Usually, DTOs are just simple classes with a set of properties we already have in our domain entities.
To reduce the amount of boilerplate code, we can use the `@DtoDefinition` to create new DTOs based on our entities.

Let's say we have `User` entity, with following fields:

* `id`
* `username`
* `password`

While it makes sense to have `password` field in our entity, so we can hash it and store it in the database, we don't really want to expose it in our API.
Doing this manually would require this kind of code:

```kotlin
data class UserDto(
    val id: UUID,
    val username: String
)

// extension function or a method in the User entity class
fun User.toDto(): UserDto = 
  UserDto(
    id = id, 
    username = username
  )
```

It may not seem like a lot of code in this example, 
but imagine having to do this for every real-world entity with multiple fields in your application.
If that's your case, you can try to use our DTO generator.

### Include/exclude fields

To generate `UserDto` class with `id` and `username` fields, and a `toDto` extension function for the `User` entity.

```kotlin
@DtoDefinition(
    from = UserDefinition::class,
    variants = [
        Variant(
            name = "UserDto", 
            mode = EXCLUDE, 
            properties = [ UserTableNames.PASSWORD ]
        )
    ]
)
private object UserDtoDefinition
```

In `variants` you can specify multiple DTOs for the same entity. Depending on your needs you may find different modes useful:

* `EXCLUDE` - includes all fields by default and excludes the ones specified in `properties`
* `INCLUDE` - excludes all fields by default and includes the ones specified in `properties`

### Implement interfaces

If you'd like to mark your DTO classes with some interfaces, you can do it like this:

```kotlin
@DtoDefinition(
    from = UserDefinition::class,
    variants = [
        Variant(
            name = "UserDtoWithAllProperties",
            implements = [ Serializable::class ]
        )
    ]
)
object UserDtoDefinition
```

Keep in mind that this solution is limited to interfaces that do not require any additional methods or properties.