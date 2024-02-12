---
outline: deep
aside: false
---

# Transactions

Sqiffy abstracts transactions to `Transaction` objects. 
Thanks to that, you can easily manage transactions in a safe way on your own, and you're not limited to some sort of a predefined transactional scope. Let's take a look on how it works. 

## Simple transaction

If all you need is a simple transaction scope, you can use the `transaction` function as follows:

```kotlin
database.transaction {
    transaction(it)
        .update(UserTable)
        // ...

    transaction(it)
        .update(UserTable)
        // ...
}
```

That's all!

## Shared transaction

If you need to share a transaction between multiple operations, you can use the `Transaction` object as follows:

```kotlin
interface UserRepository {
    fun createUser(user: UnidentifiedUser, transaction: Transaction = NoTransaction): User
    fun updateUser(userId: UserId, name: String, transaction: Transaction = NoTransaction): Boolean
}
```

With the following design, the transactional context is optional. You can implement this interface:

```kotlin
class SqlUserRepository(private val database: Database) : UserRepository {

    override fun createUser(user: UnidentifiedUser, transaction: Transaction): User =
        transaction(database)
            .insert(UserTable)
            .values(userToInsert)
            .map { userToInsert.withId(id = it[UserTable.id]) }
            .first()

    override fun updateUser(userId: UserId, name: String, transaction: Transaction): Boolean =
        transaction(database)
            .update(UserTable) { it[UserTable.name] = name }
            .where { UserTable.id eq userId }
            .execute() > 0

}
```