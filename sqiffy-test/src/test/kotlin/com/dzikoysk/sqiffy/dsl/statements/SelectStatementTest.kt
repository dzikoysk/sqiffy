package com.dzikoysk.sqiffy.dsl.statements

import com.dzikoysk.sqiffy.api.Role
import com.dzikoysk.sqiffy.domain.UnidentifiedUser
import com.dzikoysk.sqiffy.domain.toUser
import com.dzikoysk.sqiffy.dsl.*
import com.dzikoysk.sqiffy.e2e.GuildTable
import com.dzikoysk.sqiffy.e2e.UnidentifiedGuild
import com.dzikoysk.sqiffy.e2e.insert
import com.dzikoysk.sqiffy.infra.UserTable
import com.dzikoysk.sqiffy.infra.insert
import com.dzikoysk.sqiffy.specification.H2Target
import com.dzikoysk.sqiffy.specification.IntegrationSpecification
import com.dzikoysk.sqiffy.specification.MariaDbTarget
import com.dzikoysk.sqiffy.specification.MySqlTarget
import com.dzikoysk.sqiffy.specification.PostgresTarget
import com.dzikoysk.sqiffy.specification.SqliteTarget
import java.time.LocalDateTime
import java.util.UUID
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith

@ExtendWith(H2Target::class) internal class H2SelectStatementTest : SelectStatementTest()
@ExtendWith(SqliteTarget::class) internal class SqliteSelectStatementTest : SelectStatementTest()
@ExtendWith(PostgresTarget::class) internal class PostgresSelectStatementTest : SelectStatementTest()
@ExtendWith(MySqlTarget::class) internal class MySqlSelectStatementTest : SelectStatementTest()
@ExtendWith(MariaDbTarget::class) internal class MariaDbSelectStatementTest : SelectStatementTest()

internal abstract class SelectStatementTest : IntegrationSpecification() {

    private val createdAt = LocalDateTime.of(2020, 1, 1, 12, 0, 0)

    @Test
    fun `should map rows to entities`() {
        database.insert(UserTable).values(UnidentifiedUser(uuid = UUID.randomUUID(), name = "Panda", role = Role.MODERATOR, displayName = "Only Panda")).map { }

        val user = database.select(UserTable).map { it.toUser() }.first()

        assertThat(user.name).isEqualTo("Panda")
        assertThat(user.role).isEqualTo(Role.MODERATOR)
    }

    @Test
    fun `should restrict the selected columns with slice`() {
        database.insert(UserTable).values(UnidentifiedUser(uuid = UUID.randomUUID(), name = "Panda", role = Role.USER, displayName = null)).map { }

        val name = database.select(UserTable).slice(UserTable.name).map { it[UserTable.name] }.first()

        assertThat(name).isEqualTo("Panda")
    }

    @Test
    fun `should select distinct values`() {
        database.insert(UserTable).values(UnidentifiedUser(uuid = UUID.randomUUID(), name = "A", role = Role.USER, displayName = null)).map { }
        database.insert(UserTable).values(UnidentifiedUser(uuid = UUID.randomUUID(), name = "B", role = Role.USER, displayName = null)).map { }
        database.insert(UserTable).values(UnidentifiedUser(uuid = UUID.randomUUID(), name = "C", role = Role.ADMIN, displayName = null)).map { }

        val roles = database.select(UserTable).distinct().slice(UserTable.role).map { it[UserTable.role] }.toSet()

        assertThat(roles).containsExactlyInAnyOrder(Role.USER, Role.ADMIN)
    }

    @Test
    fun `should keep only matching rows with inner join`() {
        val ownerId = database.insert(UserTable).values(UnidentifiedUser(uuid = UUID.randomUUID(), name = "Owner", role = Role.USER, displayName = null)).map { it[UserTable.id] }.first()
        database.insert(UserTable).values(UnidentifiedUser(uuid = UUID.randomUUID(), name = "Loner", role = Role.USER, displayName = null)).map { }
        database.insert(GuildTable).values(UnidentifiedGuild(name = "Guild", owner = ownerId, createdAt = createdAt)).map { }

        val rows = database.select(UserTable)
            .innerJoin(UserTable.id, GuildTable.owner)
            .slice(UserTable.name, GuildTable.name)
            .map { it[UserTable.name] to it[GuildTable.name] }
            .toList()

        assertThat(rows).containsExactly("Owner" to "Guild")
    }

    @Test
    fun `should keep unmatched left rows with left join`() {
        val ownerId = database.insert(UserTable).values(UnidentifiedUser(uuid = UUID.randomUUID(), name = "Owner", role = Role.USER, displayName = null)).map { it[UserTable.id] }.first()
        database.insert(UserTable).values(UnidentifiedUser(uuid = UUID.randomUUID(), name = "Loner", role = Role.USER, displayName = null)).map { }
        database.insert(GuildTable).values(UnidentifiedGuild(name = "Guild", owner = ownerId, createdAt = createdAt)).map { }

        val names = database.select(UserTable)
            .leftJoin(UserTable.id, GuildTable.owner)
            .slice(UserTable.name, GuildTable.name)
            .map { it[UserTable.name] }
            .toList()

        assertThat(names).containsExactlyInAnyOrder("Owner", "Loner")
    }

    @Test
    fun `should keep unmatched right rows with right join`() {
        val ownerId = database.insert(UserTable).values(UnidentifiedUser(uuid = UUID.randomUUID(), name = "Owner", role = Role.USER, displayName = null)).map { it[UserTable.id] }.first()
        database.insert(UserTable).values(UnidentifiedUser(uuid = UUID.randomUUID(), name = "Loner", role = Role.USER, displayName = null)).map { }
        database.insert(GuildTable).values(UnidentifiedGuild(name = "Guild", owner = ownerId, createdAt = createdAt)).map { }

        val names = database.select(GuildTable)
            .rightJoin(GuildTable.owner, UserTable.id)
            .slice(UserTable.name, GuildTable.name)
            .map { it[UserTable.name] }
            .toList()

        assertThat(names).containsExactlyInAnyOrder("Owner", "Loner")
    }

    @OptIn(ExperimentalStdlibApi::class)
    @Test
    fun `should join with explicit conditions`() {
        val ownerId = database.insert(UserTable).values(UnidentifiedUser(uuid = UUID.randomUUID(), name = "Owner", role = Role.USER, displayName = null)).map { it[UserTable.id] }.first()
        database.insert(GuildTable).values(UnidentifiedGuild(name = "Guild", owner = ownerId, createdAt = createdAt)).map { }

        val rows = database.select(UserTable)
            .join(JoinType.INNER, GuildTable, { it.owner to UserTable.id })
            .slice(UserTable.name, GuildTable.name)
            .map { it[UserTable.name] to it[GuildTable.name] }
            .toList()

        assertThat(rows).containsExactly("Owner" to "Guild")
    }

    @Test
    fun `should group by with having`() {
        database.insert(UserTable).values(UnidentifiedUser(uuid = UUID.randomUUID(), name = "U1", role = Role.USER, displayName = null)).map { }
        database.insert(UserTable).values(UnidentifiedUser(uuid = UUID.randomUUID(), name = "U2", role = Role.USER, displayName = null)).map { }
        database.insert(UserTable).values(UnidentifiedUser(uuid = UUID.randomUUID(), name = "A1", role = Role.ADMIN, displayName = null)).map { }

        val grouped = database.select(UserTable)
            .slice(UserTable.role, UserTable.id.count())
            .groupBy(UserTable.role)
            .having { UserTable.id.count() greaterThanOrEq 2 }
            .map { it[UserTable.role] to it[UserTable.id.count()] }
            .toList()

        assertThat(grouped).containsExactly(Role.USER to 2L)
    }

    @Test
    fun `should order ascending and descending`() {
        listOf("Charlie", "Alice", "Bob").forEach {
            database.insert(UserTable).values(UnidentifiedUser(uuid = UUID.randomUUID(), name = it, role = Role.USER, displayName = null)).map { }
        }

        val ascending = database.select(UserTable).orderBy(UserTable.name to Order.ASC).map { it[UserTable.name] }.toList()
        val descending = database.select(UserTable).orderBy(UserTable.name to Order.DESC).map { it[UserTable.name] }.toList()

        assertThat(ascending).containsExactly("Alice", "Bob", "Charlie")
        assertThat(descending).containsExactly("Charlie", "Bob", "Alice")
    }

    @Test
    fun `should limit and offset`() {
        listOf("Alice", "Bob", "Charlie").forEach {
            database.insert(UserTable).values(UnidentifiedUser(uuid = UUID.randomUUID(), name = it, role = Role.USER, displayName = null)).map { }
        }

        val firstTwo = database.select(UserTable).orderBy(UserTable.name to Order.ASC).limit(2).map { it[UserTable.name] }.toList()
        val secondOnly = database.select(UserTable).orderBy(UserTable.name to Order.ASC).limit(1, offset = 1).map { it[UserTable.name] }.toList()

        assertThat(firstTwo).containsExactly("Alice", "Bob")
        assertThat(secondOnly).containsExactly("Bob")
    }

    @Test
    fun `should report row existence`() {
        assertThat(database.select(UserTable).exists()).isFalse()
        database.insert(UserTable).values(UnidentifiedUser(uuid = UUID.randomUUID(), name = "Panda", role = Role.USER, displayName = null)).map { }
        assertThat(database.select(UserTable).exists()).isTrue()
        assertThat(database.select(UserTable).where { UserTable.name eq "Panda" }.exists()).isTrue()
        assertThat(database.select(UserTable).where { UserTable.name eq "Missing" }.exists()).isFalse()
    }

    @Test
    fun `should report existence across a join`() {
        val ownerId = database.insert(UserTable).values(UnidentifiedUser(uuid = UUID.randomUUID(), name = "Owner", role = Role.USER, displayName = null)).map { it[UserTable.id] }.first()
        database.insert(GuildTable).values(UnidentifiedGuild(name = "Guild", owner = ownerId, createdAt = createdAt)).map { }

        val exists = database.select(UserTable)
            .innerJoin(UserTable.id, GuildTable.owner)
            .where { GuildTable.name eq "Guild" }
            .exists()

        assertThat(exists).isTrue()
    }

    @Test
    fun `should collect rows into a list and a set`() {
        database.insert(UserTable).values(UnidentifiedUser(uuid = UUID.randomUUID(), name = "Panda", role = Role.USER, displayName = null)).map { }
        database.insert(UserTable).values(UnidentifiedUser(uuid = UUID.randomUUID(), name = "Bamboo", role = Role.USER, displayName = null)).map { }

        val list = database.select(UserTable).orderBy(UserTable.name to Order.ASC).toList { it[UserTable.name] }
        val set = database.select(UserTable).toSet { it[UserTable.role] }

        assertThat(list).containsExactly("Bamboo", "Panda")
        assertThat(set).containsExactly(Role.USER)
    }

    @Test
    fun `should fail when mapping a column outside the slice`() {
        database.insert(UserTable).values(UnidentifiedUser(uuid = UUID.randomUUID(), name = "Panda", role = Role.USER, displayName = null)).map { }

        assertThatThrownBy {
            database.select(UserTable).slice(UserTable.name).map { it[UserTable.id] }.toList()
        }.isInstanceOf(IllegalStateException::class.java)
    }

    @Test
    fun `should run a raw query as an escape hatch`() {
        database.insert(UserTable).values(UnidentifiedUser(uuid = UUID.randomUUID(), name = "Panda", role = Role.USER, displayName = null)).map { }
        database.insert(UserTable).values(UnidentifiedUser(uuid = UUID.randomUUID(), name = "Bamboo", role = Role.USER, displayName = null)).map { }

        val names = database.rawQuery("SELECT name FROM users ORDER BY name") { it.getString("name") }

        assertThat(names).containsExactly("Bamboo", "Panda")
    }

}
