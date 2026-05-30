package com.dzikoysk.sqiffy.dsl.statements

import com.dzikoysk.sqiffy.api.Role
import com.dzikoysk.sqiffy.domain.UnidentifiedUser
import com.dzikoysk.sqiffy.domain.toUser
import com.dzikoysk.sqiffy.dsl.*
import com.dzikoysk.sqiffy.infra.UserTable
import com.dzikoysk.sqiffy.infra.insert
import com.dzikoysk.sqiffy.specification.H2Target
import com.dzikoysk.sqiffy.specification.IntegrationSpecification
import com.dzikoysk.sqiffy.specification.LinkTable
import com.dzikoysk.sqiffy.specification.MariaDbTarget
import com.dzikoysk.sqiffy.specification.MySqlTarget
import com.dzikoysk.sqiffy.specification.PostgresTarget
import com.dzikoysk.sqiffy.specification.SqliteTarget
import java.util.UUID
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith

@ExtendWith(H2Target::class) internal class H2InsertStatementTest : InsertStatementTest()
@ExtendWith(SqliteTarget::class) internal class SqliteInsertStatementTest : InsertStatementTest()
@ExtendWith(PostgresTarget::class) internal class PostgresInsertStatementTest : InsertStatementTest()
@ExtendWith(MySqlTarget::class) internal class MySqlInsertStatementTest : InsertStatementTest()
@ExtendWith(MariaDbTarget::class) internal class MariaDbInsertStatementTest : InsertStatementTest()

internal abstract class InsertStatementTest : IntegrationSpecification() {

    @Test
    fun `should return the generated key`() {
        val first = database.insert(UserTable).values(UnidentifiedUser(uuid = UUID.randomUUID(), name = "First", role = Role.USER, displayName = null)).map { it[UserTable.id] }.first()
        val second = database.insert(UserTable).values(UnidentifiedUser(uuid = UUID.randomUUID(), name = "Second", role = Role.USER, displayName = null)).map { it[UserTable.id] }.first()
        assertThat(second).isGreaterThan(first)
    }

    @Test
    fun `should insert from a value builder`() {
        database.insert(UserTable) {
            it[UserTable.uuid] = UUID.randomUUID()
            it[UserTable.name] = "Panda"
            it[UserTable.wallet] = 10f
            it[UserTable.role] = Role.MODERATOR
            it[UserTable.displayName] = "Only Panda"
        }.map { }

        val user = database.select(UserTable).map { it.toUser() }.first()
        assertThat(user.name).isEqualTo("Panda")
        assertThat(user.role).isEqualTo(Role.MODERATOR)
        assertThat(user.wallet).isEqualTo(10f)
    }

    @Test
    fun `should insert from a generated entity`() {
        database.insert(UserTable).values(UnidentifiedUser(uuid = UUID.randomUUID(), name = "Panda", wallet = 5f, role = Role.SPECTATOR, displayName = null)).map { }
        val user = database.select(UserTable).map { it.toUser() }.first()
        assertThat(user.name).isEqualTo("Panda")
        assertThat(user.role).isEqualTo(Role.SPECTATOR)
    }

    @Test
    fun `should ignore conflicting rows with insertOrIgnore`() {
        database.insert(UserTable).values(UnidentifiedUser(uuid = UUID.randomUUID(), name = "Panda", role = Role.USER, displayName = null)).map { }

        database.insertOrIgnore(UserTable, conflictingColumns = { listOf(it.name) }) {
            it[UserTable.uuid] = UUID.randomUUID()
            it[UserTable.name] = "Panda"
            it[UserTable.wallet] = 0f
            it[UserTable.role] = Role.USER
            it[UserTable.displayName] = null
        }.execute()

        val count = database.select(UserTable).slice(UserTable.id.count()).map { it[UserTable.id.count()] }.first()
        assertThat(count).isEqualTo(1)
    }

    @Test
    fun `should still insert non-conflicting rows with insertOrIgnore`() {
        database.insert(UserTable).values(UnidentifiedUser(uuid = UUID.randomUUID(), name = "Panda", role = Role.USER, displayName = null)).map { }

        database.insertOrIgnore(UserTable, conflictingColumns = { listOf(it.name) }) {
            it[UserTable.uuid] = UUID.randomUUID()
            it[UserTable.name] = "Bamboo"
            it[UserTable.wallet] = 0f
            it[UserTable.role] = Role.USER
            it[UserTable.displayName] = null
        }.execute()

        val count = database.select(UserTable).slice(UserTable.id.count()).map { it[UserTable.id.count()] }.first()
        assertThat(count).isEqualTo(2)
    }

    @Test
    fun `should insert into a table without a generated key`() {
        database.insert(LinkTable) { it[LinkTable.alpha] = 1; it[LinkTable.beta] = 2 }.execute()
        database.insert(LinkTable) { it[LinkTable.alpha] = 3; it[LinkTable.beta] = 4 }.execute()

        val rows = database.select(LinkTable).map { it[LinkTable.alpha] to it[LinkTable.beta] }.toList()
        assertThat(rows).containsExactlyInAnyOrder(1 to 2, 3 to 4)
    }

}
