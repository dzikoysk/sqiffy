@file:Suppress("RemoveRedundantQualifierName")

package com.dzikoysk.sqiffy.e2e

import com.dzikoysk.sqiffy.api.Role
import com.dzikoysk.sqiffy.api.Role.MODERATOR
import com.dzikoysk.sqiffy.definition.ChangelogProvider
import com.dzikoysk.sqiffy.definition.ChangelogProvider.LIQUIBASE
import com.dzikoysk.sqiffy.definition.ChangelogProvider.SQIFFY
import com.dzikoysk.sqiffy.dialect.postgres.PostgresDatabase
import com.dzikoysk.sqiffy.domain.TestDefault
import com.dzikoysk.sqiffy.domain.UnidentifiedUser
import com.dzikoysk.sqiffy.domain.toUser
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
import com.dzikoysk.sqiffy.dsl.notWithin
import com.dzikoysk.sqiffy.dsl.or
import com.dzikoysk.sqiffy.dsl.plus
import com.dzikoysk.sqiffy.dsl.statements.JoinType.INNER
import com.dzikoysk.sqiffy.dsl.statements.Order.ASC
import com.dzikoysk.sqiffy.dsl.sum
import com.dzikoysk.sqiffy.dsl.within
import com.dzikoysk.sqiffy.e2e.specification.SqiffyE2ETestSpecification
import com.dzikoysk.sqiffy.e2e.specification.postgresDataSource
import com.dzikoysk.sqiffy.infra.UserTable
import com.dzikoysk.sqiffy.infra.insert
import com.dzikoysk.sqiffy.shared.H2Mode.MYSQL
import com.dzikoysk.sqiffy.shared.createH2DataSource
import com.dzikoysk.sqiffy.shared.createHikariDataSource
import com.dzikoysk.sqiffy.shared.createSQLiteDataSource
import com.dzikoysk.sqiffy.transaction.NoTransaction
import com.dzikoysk.sqiffy.transaction.Transaction
import com.zaxxer.hikari.HikariDataSource
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.UUID
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.testcontainers.containers.MariaDBContainer
import org.testcontainers.containers.MySQLContainer
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import org.testcontainers.utility.DockerImageName

internal abstract class DslE2ETest(
    migrationProvider: ChangelogProvider = SQIFFY
) : SqiffyE2ETestSpecification(migrationProvider = migrationProvider) {

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
            .map { it.toUser() }
            .first()
        assertThat(userFromDatabase).isNotNull
        assertThat(userFromDatabase.name).isEqualTo("Giant Panda")
        assertThat(userFromDatabase.role).isEqualTo(Role.ADMIN)
        assertThat(userFromDatabase.wallet).isEqualTo(101f)
        assertThat(userFromDatabase.toUserDto().name).isEqualTo(userFromDatabase.name)
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
                        GuildTable.createdAt notBetween (insertedGuild.createdAt and insertedGuild.createdAt),
                        GuildTable.id within setOf(insertedGuild.id),
                        GuildTable.id notWithin setOf(insertedGuild.id + 1),
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

        val deletedCount = database.transaction { deleteGuild(insertedGuild.id, it) }
        assertThat(deletedCount).isEqualTo(1)

        val deletedCountTwo = deleteGuild(insertedGuild.id)
        assertThat(deletedCountTwo).isEqualTo(0)
    }

    private fun deleteGuild(id: Int, transaction: Transaction = NoTransaction): Int =
        transaction(database)
            .delete(GuildTable)
            .where { GuildTable.id eq id }
            .execute()

    @Test
    fun `should create table with all default values`() {
        database.generateChangeLog(
            tables = listOf(TestDefaultDefinition::class)
        )

        val testDefault = TestDefault()
        assertEquals(UUID.fromString(DefaultConstants.UUID_DEFAULT), testDefault.uuid)
        assertEquals(Role.valueOf(DefaultConstants.ENUM_DEFAULT), testDefault.enum)
        assertEquals(DefaultConstants.CHAR_DEFAULT, testDefault.char)
        assertEquals(DefaultConstants.VARCHAR_DEFAULT, testDefault.varchar)
        assertArrayEquals(DefaultConstants.BINARY_DEFAULT.toByteArray(), testDefault.binary)
        assertEquals(DefaultConstants.TEXT_DEFAULT, testDefault.text)
        assertEquals(DefaultConstants.BOOLEAN_DEFAULT, testDefault.boolean)
        assertEquals(DefaultConstants.INT_DEFAULT, testDefault.int)
        assertEquals(DefaultConstants.LONG_DEFAULT, testDefault.long)
        assertEquals(DefaultConstants.FLOAT_DEFAULT, testDefault.float)
        assertEquals(DefaultConstants.NUMERIC_DEFAULT, testDefault.numeric.toString())
        assertEquals(DefaultConstants.DECIMAL_DEFAULT, testDefault.decimal.toString())
        assertEquals(DefaultConstants.DOUBLE_DEFAULT, testDefault.double)
        assertEquals(LocalDate.parse(DefaultConstants.DATE_DEFAULT), testDefault.date)
        assertEquals(LocalDateTime.parse(DefaultConstants.DATETIME_DEFAULT), testDefault.datetime)
        assertEquals(Instant.parse(DefaultConstants.TIMESTAMP_DEFAULT), testDefault.timestamp)
    }

}

class EmbeddedPostgresUpsertDslE2ETest : SqiffyE2ETestSpecification() {
    private val postgres = postgresDataSource()
    override fun createDataSource(): HikariDataSource = postgres.dataSource
    @AfterEach fun stop() { postgres.embeddedPostgres.close() }

    @Test
    fun `should upsert`() {
        val postgresDatabase = database as PostgresDatabase

        repeat(2) { idx ->
            val (id, name, role) = postgresDatabase
                .upsert(UserTable)
                .insert {
                    it[UserTable.id] = 1
                    it[UserTable.name] = "1"
                    it[UserTable.displayName] = "1"
                    it[UserTable.uuid] = UUID.randomUUID()
                    it[UserTable.wallet] = 100f
                    it[UserTable.role] = MODERATOR
                }
                .update {
                    it[UserTable.name] = "2"
                    it[UserTable.displayName] = "2"
                }
                .execute { Triple(it[UserTable.id], it[UserTable.name], it[UserTable.role]) }
                .first()

            assertThat(id).isEqualTo(1) // conflict
            assertThat(name).isEqualTo((idx + 1).toString()) // updated field
            assertThat(role).isEqualTo(MODERATOR) // non updated field
        }
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

internal class EmbeddedPostgresWithLiquibaseDslE2ETest : DslE2ETest(migrationProvider = LIQUIBASE) {
    private val postgres = postgresDataSource()
    override fun createDataSource(): HikariDataSource = postgres.dataSource
    @AfterEach fun stop() { postgres.embeddedPostgres.close() }
}

internal class SqliteDslE2ETest : DslE2ETest() {
    override fun createDataSource(): HikariDataSource = createSQLiteDataSource()
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