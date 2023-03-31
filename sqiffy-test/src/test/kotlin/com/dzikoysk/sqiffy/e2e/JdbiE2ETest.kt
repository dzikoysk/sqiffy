@file:Suppress("RemoveRedundantQualifierName")

package com.dzikoysk.sqiffy.e2e

import com.dzikoysk.sqiffy.UnidentifiedUser
import com.dzikoysk.sqiffy.User
import com.dzikoysk.sqiffy.UserTable
import com.dzikoysk.sqiffy.UserTableNames
import com.dzikoysk.sqiffy.dsl.eq
import com.dzikoysk.sqiffy.e2e.specification.SqiffyE2ETestSpecification
import com.dzikoysk.sqiffy.shared.H2Mode.MYSQL
import com.dzikoysk.sqiffy.shared.H2Mode.POSTGRESQL
import com.dzikoysk.sqiffy.shared.createH2DataSource
import com.dzikoysk.sqiffy.shared.get
import com.dzikoysk.sqiffy.shared.toQuoted
import com.zaxxer.hikari.HikariDataSource
import org.jdbi.v3.core.kotlin.mapTo
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
        val insertedUser = database.getJdbi().withHandle<User, Exception> { handle ->
            val userToInsert = UnidentifiedUser(
                name = "Panda",
                uuid = UUID.randomUUID(),
                displayName = "Sadge"
            )

            handle
                .createUpdate(
                    database.sqlQueryGenerator.createInsertQuery(
                        tableName = UserTableNames.TABLE,
                        columns = listOf(UserTableNames.UUID, UserTableNames.NAME, UserTableNames.DISPLAYNAME)
                    ).first
                )
                .bind(UserTableNames.UUID, userToInsert.uuid)
                .bind(UserTableNames.NAME, userToInsert.name)
                .bind(UserTableNames.DISPLAYNAME, userToInsert.displayName)
                .executeAndReturnGeneratedKeys()
                .map { row -> row[UserTable.id] }
                .first()
                .let { userToInsert.withId(it) }
        }

        println("Inserted user: $insertedUser")

        val userFromDatabaseUsingRawJdbi = database.getJdbi().withHandle<User, Exception> { handle ->
            handle
                .select(
                    database.sqlQueryGenerator.createSelectQuery(
                        tableName = UserTableNames.TABLE,
                        selected = listOf(UserTableNames.ID, UserTableNames.UUID, UserTableNames.NAME, UserTableNames.DISPLAYNAME),
                        where = """${UserTableNames.NAME.toQuoted()} = :nameToMatch"""
                    ).first
                )
                .bind("nameToMatch", "Panda")
                .mapTo<User>()
                .firstOrNull()
        }

        val userFromDatabaseUsingDsl = database.select(UserTable) { UserTable.uuid eq insertedUser.uuid }
            .map {
                User(
                    id = it[UserTable.id],
                    name = it[UserTable.name],
                    uuid = it[UserTable.uuid],
                    displayName = it[UserTable.displayName]
                )
            }
            .firstOrNull()

        println("Loaded user: $userFromDatabaseUsingRawJdbi / $userFromDatabaseUsingDsl")
    }

}