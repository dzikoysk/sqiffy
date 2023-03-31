@file:Suppress("RemoveRedundantQualifierName")

package com.dzikoysk.sqiffy.e2e

import com.dzikoysk.sqiffy.UnidentifiedUser
import com.dzikoysk.sqiffy.User
import com.dzikoysk.sqiffy.UserTableNames
import com.dzikoysk.sqiffy.e2e.specification.SqiffyE2ETestSpecification
import com.dzikoysk.sqiffy.shared.H2Mode.MYSQL
import com.dzikoysk.sqiffy.shared.H2Mode.POSTGRESQL
import com.dzikoysk.sqiffy.shared.createH2DataSource
import com.zaxxer.hikari.HikariDataSource
import org.junit.jupiter.api.Test
import java.util.UUID

class H2MySQLModeE2ETest : E2ETest() {
    override fun createDataSource(): HikariDataSource = createH2DataSource(MYSQL)
}

class H2PostgreSQLModeE2ETest : E2ETest() {
    override fun createDataSource(): HikariDataSource = createH2DataSource(POSTGRESQL)
}

abstract class E2ETest : SqiffyE2ETestSpecification() {

    @Test
    fun `should insert and select entity`() {
        val insertedUser = sqiffy.getJdbi().withHandle<User, Exception> { handle ->
            val userToInsert = UnidentifiedUser(
                name = "Panda",
                uuid = UUID.randomUUID(),
                displayName = "Sadge"
            )

            handle
                .createUpdate(
                    sqiffy.sqlGenerator.createInsertQuery(
                        tableName = UserTableNames.TABLE,
                        columns = listOf(UserTableNames.UUID, UserTableNames.NAME, UserTableNames.DISPLAYNAME)
                    )
                )
                .bind(UserTableNames.UUID, userToInsert.uuid)
                .bind(UserTableNames.NAME, userToInsert.name)
                .bind(UserTableNames.DISPLAYNAME, userToInsert.displayName)
                .execute()
                .let { userToInsert.withId(it) }
        }

        println("Inserted user: $insertedUser")

        val userFromDatabase = sqiffy.getJdbi().withHandle<User, Exception> { handle ->
            handle
                .select(
                    sqiffy.sqlGenerator.createSelectQuery(
                        tableName = UserTableNames.TABLE,
                        columns = listOf(UserTableNames.ID, UserTableNames.UUID, UserTableNames.NAME, UserTableNames.DISPLAYNAME),
                        where = """"${UserTableNames.NAME}" = :nameToMatch"""
                    )
                )
                .bind("nameToMatch", "Panda")
                .mapTo(User::class.java)
                .firstOrNull()
        }

        println("Loaded user: $userFromDatabase")
    }

}