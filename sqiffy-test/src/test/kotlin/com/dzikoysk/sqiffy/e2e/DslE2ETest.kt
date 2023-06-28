@file:Suppress("RemoveRedundantQualifierName")

package com.dzikoysk.sqiffy.e2e

import com.dzikoysk.sqiffy.GuildTable
import com.dzikoysk.sqiffy.UnidentifiedGuild
import com.dzikoysk.sqiffy.api.Role
import com.dzikoysk.sqiffy.api.Role.MODERATOR
import com.dzikoysk.sqiffy.domain.UnidentifiedUser
import com.dzikoysk.sqiffy.domain.User
import com.dzikoysk.sqiffy.dsl.and
import com.dzikoysk.sqiffy.dsl.avg
import com.dzikoysk.sqiffy.dsl.between
import com.dzikoysk.sqiffy.dsl.count
import com.dzikoysk.sqiffy.dsl.eq
import com.dzikoysk.sqiffy.dsl.greaterThan
import com.dzikoysk.sqiffy.dsl.greaterThanOrEq
import com.dzikoysk.sqiffy.dsl.lessThan
import com.dzikoysk.sqiffy.dsl.lessThanOrEq
import com.dzikoysk.sqiffy.dsl.like
import com.dzikoysk.sqiffy.dsl.max
import com.dzikoysk.sqiffy.dsl.min
import com.dzikoysk.sqiffy.dsl.notBetween
import com.dzikoysk.sqiffy.dsl.notEq
import com.dzikoysk.sqiffy.dsl.notLike
import com.dzikoysk.sqiffy.dsl.or
import com.dzikoysk.sqiffy.dsl.plus
import com.dzikoysk.sqiffy.dsl.statements.JoinType.INNER
import com.dzikoysk.sqiffy.dsl.statements.Order.ASC
import com.dzikoysk.sqiffy.dsl.sum
import com.dzikoysk.sqiffy.e2e.specification.SqiffyE2ETestSpecification
import com.dzikoysk.sqiffy.e2e.specification.postgresDataSource
import com.dzikoysk.sqiffy.infra.UserTable
import com.dzikoysk.sqiffy.infra.insert
import com.dzikoysk.sqiffy.insert
import com.dzikoysk.sqiffy.shared.H2Mode.MYSQL
import com.dzikoysk.sqiffy.shared.createH2DataSource
import com.dzikoysk.sqiffy.shared.createHikariDataSource
import com.zaxxer.hikari.HikariDataSource
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.testcontainers.containers.MariaDBContainer
import org.testcontainers.containers.MySQLContainer
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import org.testcontainers.utility.DockerImageName
import java.time.LocalDateTime
import java.util.UUID

internal abstract class DslE2ETest : SqiffyE2ETestSpecification() {

    @Test
    fun `should insert and select entity`() {
        val userToInsert = UnidentifiedUser(
            name = "Panda",
            displayName = "Only Panda",
            uuid = UUID.randomUUID(),
            wallet = 100f,
            role = MODERATOR
        )

        val insertedUser = database
            .insert(UserTable)
            .values(userToInsert)
            .map { userToInsert.withId(id = it[UserTable.id]) }
            .first()

        val updatedRecords = database
            .update(UserTable) {
                it[UserTable.name] = "Giant Panda"
                it[UserTable.role] = Role.ADMIN
                it[UserTable.wallet] = UserTable.wallet + 1f
            }
            .where { UserTable.id eq insertedUser.id }
            .execute()

        assertThat(insertedUser).isNotNull
        assertThat(updatedRecords).isEqualTo(1)
        println("Inserted user: $insertedUser")

        val userFromDatabase = database.select(UserTable)
            .where { UserTable.uuid eq insertedUser.uuid }
            .map {
                User(
                    id = it[UserTable.id],
                    name = it[UserTable.name],
                    uuid = it[UserTable.uuid],
                    displayName = it[UserTable.displayName],
                    wallet = it[UserTable.wallet],
                    role = it[UserTable.role]
                )
            }
            .first()

        assertThat(userFromDatabase).isNotNull
        assertThat(userFromDatabase.name).isEqualTo("Giant Panda")
        assertThat(userFromDatabase.role).isEqualTo(Role.ADMIN)
        assertThat(userFromDatabase.wallet).isEqualTo(101f)
        println("Loaded user: $userFromDatabase")

        val guildToInsert = UnidentifiedGuild(
            name = "GLORIOUS MONKE",
            owner = userFromDatabase.id,
            createdAt = LocalDateTime.now()
        )

        val insertedGuild = database
            .insert(GuildTable)
            .values(guildToInsert)
            .map { guildToInsert.withId(id = it[GuildTable.id]) }
            .first()

        assertThat(insertedGuild).isNotNull
        println("Inserted guild: $insertedGuild")

        val joinedData = database.select(UserTable)
            .distinct()
            .join(INNER, UserTable.id, GuildTable.owner)
            .slice(UserTable.name, GuildTable.name)
            .where { GuildTable.owner eq insertedGuild.owner }
            .limit(1, offset = 0)
            .orderBy(UserTable.name to ASC)
            .map { it[UserTable.name] to it[GuildTable.name] }
            .first()

        println(joinedData)
        assertThat(joinedData).isEqualTo("Giant Panda" to "GLORIOUS MONKE")

        val matchedGuild = database.select(GuildTable)
            .slice(GuildTable.name)
            .where {
                or(
                    and(
                        GuildTable.id eq insertedGuild.id,
                        GuildTable.name notEq insertedGuild.name,
                        GuildTable.name greaterThan insertedGuild.name,
                        GuildTable.name greaterThanOrEq insertedGuild.name,
                        GuildTable.name lessThan insertedGuild.name,
                        GuildTable.name lessThanOrEq insertedGuild.name,
                        GuildTable.name like insertedGuild.name,
                        GuildTable.name notLike insertedGuild.name,
                        GuildTable.createdAt notBetween (insertedGuild.createdAt and insertedGuild.createdAt)
                    ),
                    and(
                        GuildTable.id eq GuildTable.id,
                        GuildTable.name like "G%O%N%E",
                        GuildTable.createdAt between (insertedGuild.createdAt.minusMinutes(1) and insertedGuild.createdAt.plusMinutes(1)),
                    )
                )
            }
            .map { it[GuildTable.name] }
            .firstOrNull()

        assertThat(matchedGuild).isNotNull
        println(matchedGuild)

        val aggregatedData = database.select(UserTable)
            .slice(
                UserTable.count(),
                UserTable.name.count(),
                UserTable.id.sum(),
                UserTable.id.avg(),
                UserTable.id.min(),
                UserTable.id.max()
            )
            .groupBy(UserTable.name)
            .having { UserTable.id.count() greaterThan 0 }
            .map {
                mutableMapOf(
                    "row_count" to it[UserTable.count()],
                    "count" to it[UserTable.name.count()],
                    "sum" to it[UserTable.id.sum()],
                    "avg" to it[UserTable.id.avg()],
                    "min" to it[UserTable.id.min()],
                    "max" to it[UserTable.id.max()]
                )
            }
            .first()

        assertThat(aggregatedData).isNotNull
        println(aggregatedData)

        val deletedCount = database.delete(GuildTable)
            .where { GuildTable.id eq insertedGuild.id }
            .execute()

        assertThat(deletedCount).isEqualTo(1)
    }

}

internal class H2MySQLModeDslE2ETest : DslE2ETest() {
    override fun createDataSource(): HikariDataSource = createH2DataSource(MYSQL)
}

internal class EmbeddedPostgresDslE2ETest : DslE2ETest() {
    private val postgres = postgresDataSource()
    override fun createDataSource(): HikariDataSource = postgres.dataSource
    @AfterEach fun stop() { postgres.embeddedPostgres.close() }
}

@Testcontainers
internal class PostgreSQLDslE2ETest : DslE2ETest() {
    private class SpecifiedPostgreSQLContainer(image: String) : PostgreSQLContainer<SpecifiedPostgreSQLContainer>(DockerImageName.parse(image))

    companion object {
        @Container
        private val POSTGRESQL_CONTAINER = SpecifiedPostgreSQLContainer("postgres:11.12")
    }

    override fun createDataSource(): HikariDataSource =
        createHikariDataSource(
            driver = "org.postgresql.Driver",
            url = POSTGRESQL_CONTAINER.jdbcUrl,
            username = POSTGRESQL_CONTAINER.username,
            password = POSTGRESQL_CONTAINER.password
        )
}

@Testcontainers
internal class MariaDbDslE2ETest : DslE2ETest() {
    private class SpecifiedMariaDbContainer(image: String) : MariaDBContainer<SpecifiedMariaDbContainer>(DockerImageName.parse(image))

    companion object {
        @Container
        private val MARIADB_CONTAINER = SpecifiedMariaDbContainer("mariadb:10.6.1")
    }

    override fun createDataSource(): HikariDataSource =
        createHikariDataSource(
            driver = "org.mariadb.jdbc.Driver",
            url = MARIADB_CONTAINER.jdbcUrl,
            username = MARIADB_CONTAINER.username,
            password = MARIADB_CONTAINER.password
        )
}

@Testcontainers
internal class MySQLDslE2ETest : DslE2ETest() {
    private class SpecifiedMySQLContainer(image: String) : MySQLContainer<SpecifiedMySQLContainer>(DockerImageName.parse(image))

    companion object {
        @Container
        private val MYSQL_CONTAINER = SpecifiedMySQLContainer("mysql:8.0.25")
    }

    override fun createDataSource(): HikariDataSource =
        createHikariDataSource(
            driver = "com.mysql.cj.jdbc.Driver",
            url = MYSQL_CONTAINER.jdbcUrl,
            username = MYSQL_CONTAINER.username,
            password = MYSQL_CONTAINER.password
        )
}