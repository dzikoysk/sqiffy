package com.dzikoysk.sqiffy.dsl

import com.dzikoysk.sqiffy.api.Role
import com.dzikoysk.sqiffy.domain.UnidentifiedUser
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

@ExtendWith(H2Target::class) internal class H2ExpressionTest : ExpressionTest()
@ExtendWith(SqliteTarget::class) internal class SqliteExpressionTest : ExpressionTest()
@ExtendWith(PostgresTarget::class) internal class PostgresExpressionTest : ExpressionTest()
@ExtendWith(MySqlTarget::class) internal class MySqlExpressionTest : ExpressionTest()
@ExtendWith(MariaDbTarget::class) internal class MariaDbExpressionTest : ExpressionTest()

internal abstract class ExpressionTest : IntegrationSpecification() {

    private fun namesWhere(where: () -> Expression<out Column<*>, Boolean>): List<String> =
        database.select(UserTable).where(where).map { it[UserTable.name] }.toList()

    @Test
    fun `should match with eq and exclude with notEq`() {
        database.insert(UserTable).values(UnidentifiedUser(uuid = UUID.randomUUID(), name = "Panda", role = Role.USER, displayName = null)).map { }
        database.insert(UserTable).values(UnidentifiedUser(uuid = UUID.randomUUID(), name = "Bamboo", role = Role.USER, displayName = null)).map { }
        assertThat(namesWhere { UserTable.name eq "Panda" }).containsExactly("Panda")
        assertThat(namesWhere { UserTable.name notEq "Panda" }).containsExactly("Bamboo")
    }

    @Test
    fun `should support ordering comparisons`() {
        database.insert(UserTable).values(UnidentifiedUser(uuid = UUID.randomUUID(), name = "Low", wallet = 50f, role = Role.USER, displayName = null)).map { }
        database.insert(UserTable).values(UnidentifiedUser(uuid = UUID.randomUUID(), name = "Mid", wallet = 100f, role = Role.USER, displayName = null)).map { }
        database.insert(UserTable).values(UnidentifiedUser(uuid = UUID.randomUUID(), name = "High", wallet = 150f, role = Role.USER, displayName = null)).map { }
        assertThat(namesWhere { UserTable.wallet greaterThan 100f }).containsExactly("High")
        assertThat(namesWhere { UserTable.wallet greaterThanOrEq 100f }).containsExactlyInAnyOrder("Mid", "High")
        assertThat(namesWhere { UserTable.wallet lessThan 100f }).containsExactly("Low")
        assertThat(namesWhere { UserTable.wallet lessThanOrEq 100f }).containsExactlyInAnyOrder("Low", "Mid")
    }

    @Test
    fun `should match with like and exclude with notLike`() {
        database.insert(UserTable).values(UnidentifiedUser(uuid = UUID.randomUUID(), name = "Panda", role = Role.USER, displayName = null)).map { }
        database.insert(UserTable).values(UnidentifiedUser(uuid = UUID.randomUUID(), name = "Bamboo", role = Role.USER, displayName = null)).map { }
        assertThat(namesWhere { UserTable.name like "Pan%" }).containsExactly("Panda")
        assertThat(namesWhere { UserTable.name notLike "Pan%" }).containsExactly("Bamboo")
    }

    @Test
    fun `should match case-insensitively with ilike and notIlike`() {
        database.insert(UserTable).values(UnidentifiedUser(uuid = UUID.randomUUID(), name = "Panda", role = Role.USER, displayName = null)).map { }
        database.insert(UserTable).values(UnidentifiedUser(uuid = UUID.randomUUID(), name = "Bamboo", role = Role.USER, displayName = null)).map { }
        assertThat(namesWhere { UserTable.name ilike "panda" }).containsExactly("Panda")
        assertThat(namesWhere { UserTable.name notIlike "panda" }).containsExactly("Bamboo")
    }

    @Test
    fun `should match null and non-null columns`() {
        database.insert(UserTable).values(UnidentifiedUser(uuid = UUID.randomUUID(), name = "WithDisplay", role = Role.USER, displayName = "shown")).map { }
        database.insert(UserTable).values(UnidentifiedUser(uuid = UUID.randomUUID(), name = "WithoutDisplay", role = Role.USER, displayName = null)).map { }
        assertThat(namesWhere { UserTable.displayName.isNull() }).containsExactly("WithoutDisplay")
        assertThat(namesWhere { UserTable.displayName.isNotNull() }).containsExactly("WithDisplay")
    }

    @Test
    fun `should match a range with between and exclude with notBetween`() {
        database.insert(UserTable).values(UnidentifiedUser(uuid = UUID.randomUUID(), name = "Low", wallet = 10f, role = Role.USER, displayName = null)).map { }
        database.insert(UserTable).values(UnidentifiedUser(uuid = UUID.randomUUID(), name = "Mid", wallet = 50f, role = Role.USER, displayName = null)).map { }
        database.insert(UserTable).values(UnidentifiedUser(uuid = UUID.randomUUID(), name = "High", wallet = 90f, role = Role.USER, displayName = null)).map { }
        assertThat(namesWhere { UserTable.wallet between (20f and 80f) }).containsExactly("Mid")
        assertThat(namesWhere { UserTable.wallet.notBetween(20f, 80f) }).containsExactlyInAnyOrder("Low", "High")
    }

    @Test
    fun `should match values with within and exclude with notWithin`() {
        val pandaId = database.insert(UserTable).values(UnidentifiedUser(uuid = UUID.randomUUID(), name = "Panda", role = Role.USER, displayName = null)).map { it[UserTable.id] }.first()
        val bambooId = database.insert(UserTable).values(UnidentifiedUser(uuid = UUID.randomUUID(), name = "Bamboo", role = Role.USER, displayName = null)).map { it[UserTable.id] }.first()
        database.insert(UserTable).values(UnidentifiedUser(uuid = UUID.randomUUID(), name = "Other", role = Role.USER, displayName = null)).map { }
        assertThat(namesWhere { UserTable.id within listOf(pandaId, bambooId) }).containsExactlyInAnyOrder("Panda", "Bamboo")
        assertThat(namesWhere { UserTable.id notWithin listOf(pandaId, bambooId) }).containsExactly("Other")
    }

    @Test
    fun `should treat empty within as no match and empty notWithin as all rows`() {
        database.insert(UserTable).values(UnidentifiedUser(uuid = UUID.randomUUID(), name = "Panda", role = Role.USER, displayName = null)).map { }
        database.insert(UserTable).values(UnidentifiedUser(uuid = UUID.randomUUID(), name = "Bamboo", role = Role.USER, displayName = null)).map { }
        assertThat(namesWhere { UserTable.id within emptyList() }).isEmpty()
        assertThat(namesWhere { UserTable.id notWithin emptyList() }).containsExactlyInAnyOrder("Panda", "Bamboo")
    }

    @Test
    fun `should combine conditions with and, or, not`() {
        database.insert(UserTable).values(UnidentifiedUser(uuid = UUID.randomUUID(), name = "Admin", wallet = 100f, role = Role.ADMIN, displayName = null)).map { }
        database.insert(UserTable).values(UnidentifiedUser(uuid = UUID.randomUUID(), name = "RichUser", wallet = 100f, role = Role.USER, displayName = null)).map { }
        database.insert(UserTable).values(UnidentifiedUser(uuid = UUID.randomUUID(), name = "PoorUser", wallet = 0f, role = Role.USER, displayName = null)).map { }
        assertThat(namesWhere { and(UserTable.role eq Role.USER, UserTable.wallet greaterThan 0f) }).containsExactly("RichUser")
        assertThat(namesWhere { or(UserTable.role eq Role.ADMIN, UserTable.wallet eq 0f) }).containsExactlyInAnyOrder("Admin", "PoorUser")
        assertThat(namesWhere { not(UserTable.role eq Role.USER) }).containsExactly("Admin")
    }

}
