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

@ExtendWith(H2Target::class) internal class H2UpdateStatementTest : UpdateStatementTest()
@ExtendWith(SqliteTarget::class) internal class SqliteUpdateStatementTest : UpdateStatementTest()
@ExtendWith(PostgresTarget::class) internal class PostgresUpdateStatementTest : UpdateStatementTest()
@ExtendWith(MySqlTarget::class) internal class MySqlUpdateStatementTest : UpdateStatementTest()
@ExtendWith(MariaDbTarget::class) internal class MariaDbUpdateStatementTest : UpdateStatementTest()

internal abstract class UpdateStatementTest : IntegrationSpecification() {

    @Test
    fun `should update matching rows and return the affected count`() {
        val pandaId = database.insert(UserTable).values(UnidentifiedUser(uuid = UUID.randomUUID(), name = "Panda", role = Role.USER, displayName = null)).map { it[UserTable.id] }.first()
        database.insert(UserTable).values(UnidentifiedUser(uuid = UUID.randomUUID(), name = "Bamboo", role = Role.USER, displayName = null)).map { }

        val affected = database.update(UserTable) { it[UserTable.role] = Role.ADMIN }
            .where { UserTable.id eq pandaId }
            .execute()

        assertThat(affected).isEqualTo(1)
        val admins = database.select(UserTable).where { UserTable.role eq Role.ADMIN }.map { it[UserTable.name] }.toList()
        assertThat(admins).containsExactly("Panda")
    }

    @Test
    fun `should update using a column expression`() {
        val pandaId = database.insert(UserTable).values(UnidentifiedUser(uuid = UUID.randomUUID(), name = "Panda", wallet = 100f, role = Role.USER, displayName = null)).map { it[UserTable.id] }.first()

        database.update(UserTable) { it[UserTable.wallet] = UserTable.wallet + 50f }
            .where { UserTable.id eq pandaId }
            .execute()

        val wallet = database.select(UserTable).where { UserTable.id eq pandaId }.map { it[UserTable.wallet] }.first()
        assertThat(wallet).isEqualTo(150f)
    }

    @Test
    fun `should update with nested arithmetic preserving grouping`() {
        val pandaId = database.insert(UserTable).values(UnidentifiedUser(uuid = UUID.randomUUID(), name = "Panda", wallet = 100f, role = Role.USER, displayName = null)).map { it[UserTable.id] }.first()

        // (100 + 10) * 2 = 220, not 100 + (10 * 2)
        database.update(UserTable) { it[UserTable.wallet] = (UserTable.wallet + 10f) * 2f }
            .where { UserTable.id eq pandaId }
            .execute()

        val wallet = database.select(UserTable).where { UserTable.id eq pandaId }.map { it[UserTable.wallet] }.first()
        assertThat(wallet).isEqualTo(220f)
    }

    @Test
    fun `should update all rows without a condition`() {
        database.insert(UserTable).values(UnidentifiedUser(uuid = UUID.randomUUID(), name = "Panda", role = Role.USER, displayName = null)).map { }
        database.insert(UserTable).values(UnidentifiedUser(uuid = UUID.randomUUID(), name = "Bamboo", role = Role.USER, displayName = null)).map { }

        val affected = database.update(UserTable) { it[UserTable.role] = Role.SPECTATOR }.execute()

        assertThat(affected).isEqualTo(2)
    }

}
