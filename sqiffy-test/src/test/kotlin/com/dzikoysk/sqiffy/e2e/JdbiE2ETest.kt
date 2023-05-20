@file:Suppress("RemoveRedundantQualifierName")

package com.dzikoysk.sqiffy.e2e

import com.dzikoysk.sqiffy.Dialect.POSTGRESQL
import com.dzikoysk.sqiffy.Role
import com.dzikoysk.sqiffy.UnidentifiedUser
import com.dzikoysk.sqiffy.User
import com.dzikoysk.sqiffy.UserTableNames
import com.dzikoysk.sqiffy.e2e.specification.SqiffyE2ETestSpecification
import com.dzikoysk.sqiffy.e2e.specification.postgresDataSource
import com.dzikoysk.sqiffy.shared.H2Mode.MYSQL
import com.dzikoysk.sqiffy.shared.createH2DataSource
import com.dzikoysk.sqiffy.shared.multiline
import com.zaxxer.hikari.HikariDataSource
import org.assertj.core.api.Assertions.assertThat
import org.jdbi.v3.core.kotlin.mapTo
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import java.util.UUID

internal class H2MySQLModeJdbiE2ETest : JdbiE2ETest() {
    override fun createDataSource(): HikariDataSource = createH2DataSource(MYSQL)
}

internal class EmbeddedPostgresJdbiE2ETest : JdbiE2ETest() {
    val postgres = postgresDataSource()
    override fun createDataSource(): HikariDataSource = postgres.dataSource
    @AfterEach fun stop() { postgres.pg.close() }
}

internal abstract class JdbiE2ETest : SqiffyE2ETestSpecification() {

    @Test
    fun `should insert and select entity`() {
        val insertedUser = database.getJdbi().withHandle<User, Exception> { handle ->
            val userToInsert = UnidentifiedUser(
                name = "Panda",
                uuid = UUID.randomUUID(),
                displayName = "Only Panda",
                role = Role.MODERATOR
            )

            handle
                .createUpdate(multiline("""
                    INSERT INTO "${UserTableNames.TABLE}" 
                    ("${UserTableNames.UUID}", "${UserTableNames.NAME}", "${UserTableNames.DISPLAYNAME}", "${UserTableNames.ROLE}")
                    VALUES (:0, :1, :2, :3${when (database.dialect) {
                        POSTGRESQL -> "::${Role.TYPE_NAME}" // jdbc requires explicit casts for enums in postgres
                        else -> ""
                    }})
                """))
                .bind("0", userToInsert.uuid)
                .bind("1", userToInsert.name)
                .bind("2", userToInsert.displayName)
                .bind("3", userToInsert.role)
                .executeAndReturnGeneratedKeys()
                .map { row -> row.getColumn(UserTableNames.ID, Int::class.javaObjectType) }
                .first()
                .let { userToInsert.withId(it) }
        }

        println("Inserted user: $insertedUser")

        val userFromDatabaseUsingRawJdbi = database.getJdbi().withHandle<User, Exception> { handle ->
            handle
                .select(multiline("""
                    SELECT *
                    FROM "${UserTableNames.TABLE}" 
                    WHERE "${UserTableNames.NAME}" = :nameToMatch
                """))
                .bind("nameToMatch", "Panda")
                .mapTo<User>()
                .firstOrNull()
        }

        println("Loaded user: $userFromDatabaseUsingRawJdbi")
        assertThat(insertedUser).isNotNull
        assertThat(userFromDatabaseUsingRawJdbi).isNotNull
        assertThat(userFromDatabaseUsingRawJdbi).isEqualTo(insertedUser)
    }

}