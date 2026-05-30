package com.dzikoysk.sqiffy.dsl.statements

import com.dzikoysk.sqiffy.api.Role
import com.dzikoysk.sqiffy.domain.UnidentifiedUser
import com.dzikoysk.sqiffy.dsl.*
import com.dzikoysk.sqiffy.infra.UserTable
import com.dzikoysk.sqiffy.infra.insert
import com.dzikoysk.sqiffy.specification.H2Target
import com.dzikoysk.sqiffy.specification.IntegrationSpecification
import com.dzikoysk.sqiffy.specification.MariaDbTarget
import com.dzikoysk.sqiffy.specification.MySqlTarget
import com.dzikoysk.sqiffy.specification.PostgresTarget
import com.dzikoysk.sqiffy.specification.SqliteTarget
import java.util.UUID
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith

@ExtendWith(H2Target::class) internal class H2DeleteStatementTest : DeleteStatementTest()
@ExtendWith(SqliteTarget::class) internal class SqliteDeleteStatementTest : DeleteStatementTest()
@ExtendWith(PostgresTarget::class) internal class PostgresDeleteStatementTest : DeleteStatementTest()
@ExtendWith(MySqlTarget::class) internal class MySqlDeleteStatementTest : DeleteStatementTest()
@ExtendWith(MariaDbTarget::class) internal class MariaDbDeleteStatementTest : DeleteStatementTest()

internal abstract class DeleteStatementTest : IntegrationSpecification() {

    @Test
    fun `should delete matching rows and return the affected count`() {
        val pandaId = database.insert(UserTable).values(UnidentifiedUser(uuid = UUID.randomUUID(), name = "Panda", role = Role.USER, displayName = null)).map { it[UserTable.id] }.first()
        database.insert(UserTable).values(UnidentifiedUser(uuid = UUID.randomUUID(), name = "Bamboo", role = Role.USER, displayName = null)).map { }

        val affected = database.delete(UserTable).where { UserTable.id eq pandaId }.execute()

        assertThat(affected).isEqualTo(1)
        val remaining = database.select(UserTable).slice(UserTable.id.count()).map { it[UserTable.id.count()] }.first()
        assertThat(remaining).isEqualTo(1)
    }

    @Test
    fun `should delete all rows without a condition`() {
        database.insert(UserTable).values(UnidentifiedUser(uuid = UUID.randomUUID(), name = "Panda", role = Role.USER, displayName = null)).map { }
        database.insert(UserTable).values(UnidentifiedUser(uuid = UUID.randomUUID(), name = "Bamboo", role = Role.USER, displayName = null)).map { }

        val affected = database.delete(UserTable).execute()

        assertThat(affected).isEqualTo(2)
        val remaining = database.select(UserTable).slice(UserTable.id.count()).map { it[UserTable.id.count()] }.first()
        assertThat(remaining).isEqualTo(0)
    }

}
