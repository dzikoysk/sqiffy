@file:Suppress("RemoveRedundantQualifierName")

package com.dzikoysk.sqiffy.e2e

import com.dzikoysk.sqiffy.Dialect.POSTGRESQL
import com.dzikoysk.sqiffy.api.Role
import com.dzikoysk.sqiffy.domain.UnidentifiedUser
import com.dzikoysk.sqiffy.domain.User
import com.dzikoysk.sqiffy.e2e.specification.SqiffyE2ETestSpecification
import com.dzikoysk.sqiffy.e2e.specification.postgresDataSource
import com.dzikoysk.sqiffy.infra.UserTableNames
import com.dzikoysk.sqiffy.shared.multiline
import com.zaxxer.hikari.HikariDataSource
import java.util.UUID
import org.assertj.core.api.Assertions.assertThat
import org.jdbi.v3.core.kotlin.mapTo
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test

internal class EmbeddedPostgresJdbiE2ETest : JdbiE2ETest() {
    private val postgres = postgresDataSource()
    override fun createDataSource(): HikariDataSource = postgres.dataSource
    @AfterEach fun stop() { postgres.embeddedPostgres.close() }
}

internal abstract class JdbiE2ETest : SqiffyE2ETestSpecification() {

    @Test
    fun `should insert and select entity`() {
        val insertedUser = database.getJdbi().withHandle<User, Exception> { handle ->
            val userToInsert = UnidentifiedUser(
                name = "Panda",
                uuid = UUID.randomUUID(),
                displayName = "Only Panda",
                role = Role.MODERATOR,
                wallet = 100f
            )

            handle
                .createUpdate(multiline("""
                    INSERT INTO "${UserTableNames.TABLE}" 
                    ("${UserTableNames.UUID}", "${UserTableNames.NAME}", "${UserTableNames.DISPLAYNAME}", "${UserTableNames.ROLE}", "${UserTableNames.WALLET}")
                    VALUES (:0, :1, :2, :3${when (database.getDialect()) {
                        POSTGRESQL -> "::${Role.TYPE_NAME}" // jdbc requires explicit casts for enums in postgres
                        else -> ""
                    }}, :4)
                """))
                .bind("0", userToInsert.uuid)
                .bind("1", userToInsert.name)
                .bind("2", userToInsert.displayName)
                .bind("3", userToInsert.role)
                .bind("4", userToInsert.wallet)
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