@file:Suppress("RemoveRedundantQualifierName")

package com.dzikoysk.sqiffy.e2e

import com.dzikoysk.sqiffy.GuildTable
import com.dzikoysk.sqiffy.Role
import com.dzikoysk.sqiffy.UnidentifiedGuild
import com.dzikoysk.sqiffy.UnidentifiedUser
import com.dzikoysk.sqiffy.User
import com.dzikoysk.sqiffy.UserTable
import com.dzikoysk.sqiffy.dsl.eq
import com.dzikoysk.sqiffy.dsl.statements.JoinType.INNER
import com.dzikoysk.sqiffy.e2e.specification.SqiffyE2ETestSpecification
import com.dzikoysk.sqiffy.e2e.specification.postgresDataSource
import com.dzikoysk.sqiffy.shared.H2Mode.MYSQL
import com.dzikoysk.sqiffy.shared.createH2DataSource
import com.zaxxer.hikari.HikariDataSource
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import java.util.UUID
import kotlin.collections.set

class H2MySQLModeDslE2ETest : DslE2ETest() {
    override fun createDataSource(): HikariDataSource = createH2DataSource(MYSQL)
}

class EmbeddedPostgresDslE2ETest : DslE2ETest() {
    val postgres = postgresDataSource()
    override fun createDataSource(): HikariDataSource = postgres.dataSource
    @AfterEach fun stop() { postgres.pg.close() }
}

abstract class DslE2ETest : SqiffyE2ETestSpecification() {

    @Test
    fun `should insert and select entity`() {
        val userToInsert = UnidentifiedUser(
            name = "Panda",
            displayName = "Only Panda",
            uuid = UUID.randomUUID(),
            role = Role.MODERATOR
        )

        val insertedUserWithDsl = database
            .insert(UserTable) {
                it[UserTable.uuid] = userToInsert.uuid
                it[UserTable.name] = userToInsert.name
                it[UserTable.displayName] = userToInsert.displayName
                it[UserTable.role] = userToInsert.role
            }
            .map { userToInsert.withId(id = it[UserTable.id]) }
            .first()

        val guildToInsert = UnidentifiedGuild(
            name = "MONKE",
            owner = insertedUserWithDsl.id
        )

        val insertedGuild = database
            .insert(GuildTable) {
                it[GuildTable.name] = guildToInsert.name
                it[GuildTable.owner] = guildToInsert.owner
            }
            .map { guildToInsert.withId(id = it[GuildTable.id]) }
            .first()

        println("Inserted user: $insertedUserWithDsl")

        val userFromDatabaseUsingDsl = database.select(UserTable)
            .where { UserTable.uuid eq insertedUserWithDsl.uuid }
            .map {
                User(
                    id = it[UserTable.id],
                    name = it[UserTable.name],
                    uuid = it[UserTable.uuid],
                    displayName = it[UserTable.displayName],
                    role = it[UserTable.role]
                )
            }
            .firstOrNull()

        println("Loaded user: $userFromDatabaseUsingDsl")

        val joinedData = database.select(UserTable)
            .join(INNER, UserTable.id, GuildTable.owner)
            .where { GuildTable.owner eq insertedGuild.owner }
            .map { it[UserTable.name] to it[GuildTable.name] }
            .first()

        println(joinedData)

        assertThat(insertedUserWithDsl).isNotNull
        assertThat(userFromDatabaseUsingDsl).isNotNull
        assertThat(insertedUserWithDsl).isEqualTo(userFromDatabaseUsingDsl)
        assertThat(joinedData).isEqualTo("Panda" to "MONKE")

        val deletedCount = database.delete(GuildTable)
            .where { GuildTable.id eq insertedGuild.id }
            .execute()

        assertThat(deletedCount).isEqualTo(1)
    }

}