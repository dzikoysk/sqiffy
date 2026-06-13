package com.dzikoysk.sqiffy.dialect.postgres

import com.dzikoysk.sqiffy.api.Role
import com.dzikoysk.sqiffy.domain.UnidentifiedUser
import com.dzikoysk.sqiffy.infra.UserTable
import com.dzikoysk.sqiffy.infra.insert
import com.dzikoysk.sqiffy.specification.IntegrationSpecification
import com.dzikoysk.sqiffy.specification.PostgresTarget
import java.util.UUID
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith

/**
 * Tests for the PostgreSQL-specific select API (`NULLS FIRST` / `NULLS LAST` ordering),
 * which is only available on a [PostgresDatabase] and therefore not part of the cross-dialect suite.
 */
@ExtendWith(PostgresTarget::class)
internal class SelectStatementTest : IntegrationSpecification() {

    private val postgresDatabase: PostgresDatabase
        get() = database as PostgresDatabase

    @Test
    fun `should order with nulls first and nulls last`() {
        database.insert(UserTable).values(UnidentifiedUser(uuid = UUID.randomUUID(), name = "Alice", role = Role.USER, displayName = "A")).map { }
        database.insert(UserTable).values(UnidentifiedUser(uuid = UUID.randomUUID(), name = "Bob", role = Role.USER, displayName = null)).map { }
        database.insert(UserTable).values(UnidentifiedUser(uuid = UUID.randomUUID(), name = "Charlie", role = Role.USER, displayName = "C")).map { }

        val nullsFirst = postgresDatabase.select(UserTable)
            .orderBy { UserTable.displayName.asc().nullsFirst() }
            .map { it[UserTable.name] }
            .toList()

        val nullsLast = postgresDatabase.select(UserTable)
            .orderBy { UserTable.displayName.asc().nullsLast() }
            .map { it[UserTable.name] }
            .toList()

        assertThat(nullsFirst.first()).isEqualTo("Bob")
        assertThat(nullsLast.last()).isEqualTo("Bob")
    }

    @Test
    fun `should order by multiple columns with nulls`() {
        database.insert(UserTable).values(UnidentifiedUser(uuid = UUID.randomUUID(), name = "A", role = Role.USER, displayName = "X")).map { }
        database.insert(UserTable).values(UnidentifiedUser(uuid = UUID.randomUUID(), name = "B", role = Role.USER, displayName = null)).map { }
        database.insert(UserTable).values(UnidentifiedUser(uuid = UUID.randomUUID(), name = "C", role = Role.USER, displayName = "X")).map { }

        val ordered = postgresDatabase.select(UserTable)
            .orderBy {
                UserTable.displayName.asc().nullsFirst()
                UserTable.name.desc()
            }
            .map { it[UserTable.name] }
            .toList()

        // displayName ASC NULLS FIRST puts B (null) first, then the two "X" rows ordered by name DESC -> C, A
        assertThat(ordered).containsExactly("B", "C", "A")
    }

}
