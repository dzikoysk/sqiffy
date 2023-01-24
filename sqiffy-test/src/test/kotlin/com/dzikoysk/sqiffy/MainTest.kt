@file:Suppress("RemoveRedundantQualifierName")

package com.dzikoysk.sqiffy

import com.dzikoysk.sqiffy.ConstraintDefinitionType.REMOVE_CONSTRAINT
import com.dzikoysk.sqiffy.ConstraintType.FOREIGN_KEY
import com.dzikoysk.sqiffy.ConstraintType.PRIMARY_KEY
import com.dzikoysk.sqiffy.DataType.INT
import com.dzikoysk.sqiffy.DataType.UUID_VARCHAR
import com.dzikoysk.sqiffy.DataType.VARCHAR
import com.dzikoysk.sqiffy.IndexDefinitionOperation.REMOVE_INDEX
import com.dzikoysk.sqiffy.IndexType.INDEX
import com.dzikoysk.sqiffy.IndexType.UNIQUE_INDEX
import com.dzikoysk.sqiffy.PropertyDefinitionOperation.RENAME
import com.dzikoysk.sqiffy.PropertyDefinitionOperation.RETYPE
import com.dzikoysk.sqiffy.Versions.V_1_0_0
import com.dzikoysk.sqiffy.Versions.V_1_0_1
import com.dzikoysk.sqiffy.Versions.V_1_0_2
import com.dzikoysk.sqiffy.changelog.ChangeLogGenerator
import com.dzikoysk.sqiffy.shared.createTestDatabaseFile
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.select
import org.jetbrains.exposed.sql.transactions.transaction
import org.junit.jupiter.api.Test
import java.util.UUID
import kotlin.io.path.absolutePathString

private object Versions {
    const val V_1_0_0 = "1.0.0"
    const val V_1_0_1 = "1.0.1"
    const val V_1_0_2 = "1.0.2"
}

@Definition([
    DefinitionVersion(
        version = V_1_0_0,
        name = "guilds_table",
        properties = [
            Property(name = "id", type = INT, autoincrement = true),
            Property(name = "owner", type = INT)
        ],
        constraints = [
            Constraint(type = FOREIGN_KEY, on = "id", name = "fk_id", referenced = UserDefinition::class, references = "id")
        ]
    ),
    DefinitionVersion(
        version = V_1_0_1,
        constraints = [
            Constraint(REMOVE_CONSTRAINT, type = FOREIGN_KEY, name = "fk_id")
        ]
    ),
    DefinitionVersion(
        version = V_1_0_2,
        constraints = [
            Constraint(type = FOREIGN_KEY, on = "id", name = "fk_id", referenced = UserDefinition::class, references = "id")
        ]
    )
])
object GuildDefinition

@Definition([
    DefinitionVersion(
        version = V_1_0_0,
        name = "users_table",
        properties = [
            Property(name = "id", type = INT, autoincrement = true),
            Property(name = "uuid", type = UUID_VARCHAR),
            Property(name = "name", type = VARCHAR, details = "12"),
        ],
        constraints = [
            Constraint(type = PRIMARY_KEY, name = "pk_id", on = "id"),
        ],
        indices = [
            Index(type = INDEX, name = "idx_id", columns = ["id"]),
            Index(type = UNIQUE_INDEX, name = "uq_name", columns = ["name"])
        ]
    ),
    DefinitionVersion(
        version = V_1_0_1,
        properties = [
            Property(operation = RETYPE, name = "name", type = VARCHAR, details = "24"),
            Property(name = "display_name", type = VARCHAR, details = "48", nullable = true)
        ],
        indices = [
            Index(operation = REMOVE_INDEX, type = INDEX, name = "idx_id"),
            Index(type = INDEX, name = "idx_id", columns = ["id"])
]
    ),
    DefinitionVersion(
        version = V_1_0_2,
        properties = [
            Property(operation = RENAME, name = "display_name", rename = "displayName")
        ]
    )
])
object UserDefinition

class MainTest {

    @Test
    fun testBaseSchemeGenerator() {
        val baseSchemeGenerator = ChangeLogGenerator(RuntimeTypeFactory())
        val changeLog = baseSchemeGenerator.generateChangeLog(UserDefinition::class, GuildDefinition::class)

        changeLog.changes.forEach { (version, changes) ->
            println(version)
            changes.forEach { println("  $it") }
        }
    }

    @Test
    fun test() {
        val dataSource = createDataSource(
            driver = "org.h2.Driver",
            url = "jdbc:h2:${createTestDatabaseFile("test-database").absolutePathString()};MODE=MYSQL",
            threadPool = 1
        )

        dataSource.toDatabaseConnection().use { databaseConnection ->
            transaction(databaseConnection.database) {
                val changeLog = ChangeLogGenerator(RuntimeTypeFactory()).generateChangeLog(UserDefinition::class, GuildDefinition::class)
                changeLog.runMigrations(databaseConnection.database)

                // generated entity
                val user = User(
                    id = 69,
                    name = "Panda",
                    uuid = UUID.randomUUID(),
                    displayName = "Sadge"
                )

                UserTable.insert {
                    it[UserTable.id] = user.id
                    it[UserTable.name] = user.name
                    it[UserTable.uuid] = user.uuid
                    it[UserTable.displayName] = user.displayName
                }

                val userFromDatabase = UserTable.select { UserTable.name eq "Panda" }
                    .first()
                    .let {
                        User(
                            id = it[UserTable.id],
                            name = it[UserTable.name],
                            uuid = it[UserTable.uuid],
                            displayName = it[UserTable.displayName]
                        )
                    }

                println(userFromDatabase)
            }
        }
    }

}