package com.dzikoysk.sqiffy.transaction

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

@ExtendWith(H2Target::class) internal class H2TransactionTest : TransactionTest()
@ExtendWith(SqliteTarget::class) internal class SqliteTransactionTest : TransactionTest()
@ExtendWith(PostgresTarget::class) internal class PostgresTransactionTest : TransactionTest()
@ExtendWith(MySqlTarget::class) internal class MySqlTransactionTest : TransactionTest()
@ExtendWith(MariaDbTarget::class) internal class MariaDbTransactionTest : TransactionTest()

internal abstract class TransactionTest : IntegrationSpecification() {

    private fun userCount(): Long =
        database.select(UserTable).slice(UserTable.id.count()).map { it[UserTable.id.count()] }.first()

    @Test
    fun `should commit work done inside a transaction`() {
        database.transaction { transaction ->
            database.with(transaction)
                .insert(UserTable)
                .values(UnidentifiedUser(uuid = UUID.randomUUID(), name = "Panda", role = Role.USER, displayName = null))
                .map { }
        }

        assertThat(userCount()).isEqualTo(1)
    }

    @Test
    fun `should roll back a transaction on exception`() {
        runCatching {
            database.transaction { transaction ->
                database.with(transaction)
                    .insert(UserTable)
                    .values(UnidentifiedUser(uuid = UUID.randomUUID(), name = "Panda", role = Role.USER, displayName = null))
                    .map { }
                throw IllegalStateException("boom")
            }
        }

        assertThat(userCount()).isEqualTo(0)
    }

}
