@file:Suppress("RemoveRedundantQualifierName")

package com.dzikoysk.sqiffy.e2e

import com.dzikoysk.sqiffy.UnidentifiedUser
import com.dzikoysk.sqiffy.User
import com.dzikoysk.sqiffy.UserTableNames
import com.dzikoysk.sqiffy.e2e.specification.SqiffyE2ETestSpecification
import com.dzikoysk.sqiffy.shared.H2Mode.MYSQL
import com.dzikoysk.sqiffy.shared.H2Mode.POSTGRESQL
import com.dzikoysk.sqiffy.shared.createH2DataSource
import com.dzikoysk.sqiffy.shared.multiline
import com.zaxxer.hikari.HikariDataSource
import org.assertj.core.api.Assertions.assertThat
import org.jdbi.v3.core.kotlin.mapTo
import org.junit.jupiter.api.Test
import java.util.UUID

class H2MySQLModeJdbiE2ETest : JdbiE2ETest() {
    override fun createDataSource(): HikariDataSource = createH2DataSource(MYSQL)
}

class H2PostgreSQLModeJdbiE2ETest : JdbiE2ETest() {
    override fun createDataSource(): HikariDataSource = createH2DataSource(POSTGRESQL)
}

abstract class JdbiE2ETest : SqiffyE2ETestSpecification() {

    @Test
    fun `should insert and select entity`() {
        val insertedUser = database.getJdbi().withHandle<User, Exception> { handle ->
            val userToInsert = UnidentifiedUser(
                name = "Panda",
                uuid = UUID.randomUUID(),
                displayName = "Only Panda"
            )

            handle
                .createUpdate(multiline("""
                    INSERT INTO "${UserTableNames.TABLE}" 
                    ("${UserTableNames.UUID}", "${UserTableNames.NAME}", "${UserTableNames.DISPLAYNAME}")
                     VALUES (:0, :1, :2)
                """))
                .bind("0", userToInsert.uuid)
                .bind("1", userToInsert.name)
                .bind("2", userToInsert.displayName)
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