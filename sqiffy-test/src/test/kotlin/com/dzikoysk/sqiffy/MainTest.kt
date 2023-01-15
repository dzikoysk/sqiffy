@file:Suppress("RemoveRedundantQualifierName")

package com.dzikoysk.sqiffy

import com.dzikoysk.sqiffy.ConstraintType.FOREIGN_KEY
import com.dzikoysk.sqiffy.ConstraintType.PRIMARY_KEY
import com.dzikoysk.sqiffy.DataType.INT
import com.dzikoysk.sqiffy.DataType.UUID_VARCHAR
import com.dzikoysk.sqiffy.DataType.VARCHAR
import com.dzikoysk.sqiffy.IndexType.UNIQUE_INDEX
import com.dzikoysk.sqiffy.PropertyDefinitionType.RENAME
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.select
import org.jetbrains.exposed.sql.transactions.transaction
import org.junit.jupiter.api.Test
import java.io.File
import java.util.UUID
import kotlin.io.path.absolutePathString


@Definition([
    DefinitionVersion(
        version = "1.0.0",
        name = "guilds_table",
        properties = [
            Property(name = "id", type = INT, autoincrement = true),
            Property(name = "owner", type = INT)
        ],
        constraints = [
            Constraint(FOREIGN_KEY, referenced = UserDefinition::class, on = "id")
        ]
    )
])
object GuildDefinition

@Definition([
    DefinitionVersion(
        version = "1.0.0",
        name = "users_table",
        properties = [
            Property(name = "id", type = INT, autoincrement = true),
            Property(name = "uuid", type = UUID_VARCHAR),
            Property(name = "name", type = VARCHAR, details = "16"),
        ],
        constraints = [
            Constraint(PRIMARY_KEY, on = "id"),
        ],
        indices = [
            Index(UNIQUE_INDEX, columns = ["name"])
        ]
    ),
    DefinitionVersion(
        version = "1.0.1",
        properties = [
            Property(name = "display_name", type = VARCHAR, details = "48", nullable = true)
        ]
    ),
    DefinitionVersion(
        version = "1.0.2",
        properties = [
            Property(RENAME, name = "display_name", rename = "displayName")
        ]
    )
])
object UserDefinition

class MainTest {

    @Test
    fun test() {
        val dbFile = File.createTempFile("test-database", ".db")
            .also { it.deleteOnExit() }
            .toPath()

        val dataSource = createDataSource(
            driver = "org.h2.Driver",
            url = "jdbc:h2:${dbFile.absolutePathString()};MODE=MYSQL",
            threadPool = 1
        )

        dataSource
            .toDatabaseConnection()
            .use { databaseConnection ->
                val user = User(
                    id = 69,
                    name = "Panda",
                    uuid = UUID.randomUUID(),
                    displayName = "Sadge"
                )

                transaction {
                    UserTable.insert {
                        it[UserTable.id] = user.id
                        it[UserTable.name] = user.name
                        it[UserTable.uuid] = user.uuid
                        it[UserTable.displayName] = user.displayName
                    }
                }

                val userFromDatabase = transaction(databaseConnection.database) {
                    UserTable.select { UserTable.displayName eq "Panda" }
                        .first()
                        .let {
                            User(
                                id = it[UserTable.id],
                                name = it[UserTable.name],
                                uuid = it[UserTable.uuid],
                                displayName = it[UserTable.displayName]
                            )
                        }
                }

                println(userFromDatabase)
            }
    }

}