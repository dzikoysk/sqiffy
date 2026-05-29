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

@ExtendWith(H2Target::class) internal class H2AggregationTest : AggregationTest()
@ExtendWith(SqliteTarget::class) internal class SqliteAggregationTest : AggregationTest()
@ExtendWith(PostgresTarget::class) internal class PostgresAggregationTest : AggregationTest()
@ExtendWith(MySqlTarget::class) internal class MySqlAggregationTest : AggregationTest()
@ExtendWith(MariaDbTarget::class) internal class MariaDbAggregationTest : AggregationTest()

internal abstract class AggregationTest : IntegrationSpecification() {

    @Test
    fun `should count rows`() {
        database.insert(UserTable).values(UnidentifiedUser(uuid = UUID.randomUUID(), name = "Panda", role = Role.USER, displayName = null)).map { }
        database.insert(UserTable).values(UnidentifiedUser(uuid = UUID.randomUUID(), name = "Bamboo", role = Role.USER, displayName = null)).map { }

        val tableCount = database.select(UserTable).slice(UserTable.count()).map { it[UserTable.count()] }.first()
        val columnCount = database.select(UserTable).slice(UserTable.id.count()).map { it[UserTable.id.count()] }.first()

        assertThat(tableCount).isEqualTo(2)
        assertThat(columnCount).isEqualTo(2)
    }

    @Test
    fun `should count distinct values`() {
        database.insert(UserTable).values(UnidentifiedUser(uuid = UUID.randomUUID(), name = "A", role = Role.USER, displayName = null)).map { }
        database.insert(UserTable).values(UnidentifiedUser(uuid = UUID.randomUUID(), name = "B", role = Role.USER, displayName = null)).map { }
        database.insert(UserTable).values(UnidentifiedUser(uuid = UUID.randomUUID(), name = "C", role = Role.ADMIN, displayName = null)).map { }

        val distinctRoles = database.select(UserTable).slice(UserTable.role.countDistinct()).map { it[UserTable.role.countDistinct()] }.first()

        assertThat(distinctRoles).isEqualTo(2)
    }

    @Test
    fun `should compute sum, min and max`() {
        database.insert(UserTable).values(UnidentifiedUser(uuid = UUID.randomUUID(), name = "A", wallet = 10f, role = Role.USER, displayName = null)).map { }
        database.insert(UserTable).values(UnidentifiedUser(uuid = UUID.randomUUID(), name = "B", wallet = 20f, role = Role.USER, displayName = null)).map { }
        database.insert(UserTable).values(UnidentifiedUser(uuid = UUID.randomUUID(), name = "C", wallet = 30f, role = Role.USER, displayName = null)).map { }

        val row = database.select(UserTable)
            .slice(UserTable.wallet.sum(), UserTable.wallet.min(), UserTable.wallet.max())
            .map { Triple(it[UserTable.wallet.sum()], it[UserTable.wallet.min()], it[UserTable.wallet.max()]) }
            .first()

        assertThat(row.first).isEqualTo(60L)
        assertThat(row.second).isEqualTo(10f)
        assertThat(row.third).isEqualTo(30f)
    }

}
