package com.dzikoysk.sqiffy.processor

import com.dzikoysk.sqiffy.specification.H2Target
import com.dzikoysk.sqiffy.specification.HasLabel
import com.dzikoysk.sqiffy.specification.IntegrationSpecification
import com.dzikoysk.sqiffy.specification.MariaDbTarget
import com.dzikoysk.sqiffy.specification.MySqlTarget
import com.dzikoysk.sqiffy.specification.PostgresTarget
import com.dzikoysk.sqiffy.specification.Priority
import com.dzikoysk.sqiffy.specification.SqliteTarget
import com.dzikoysk.sqiffy.specification.TaggedTable
import com.dzikoysk.sqiffy.specification.toTagged
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith

@ExtendWith(H2Target::class) internal class H2GeneratedEntitiesTest : GeneratedEntitiesTest()
@ExtendWith(SqliteTarget::class) internal class SqliteGeneratedEntitiesTest : GeneratedEntitiesTest()
@ExtendWith(PostgresTarget::class) internal class PostgresGeneratedEntitiesTest : GeneratedEntitiesTest()
@ExtendWith(MySqlTarget::class) internal class MySqlGeneratedEntitiesTest : GeneratedEntitiesTest()
@ExtendWith(MariaDbTarget::class) internal class MariaDbGeneratedEntitiesTest : GeneratedEntitiesTest()

internal abstract class GeneratedEntitiesTest : IntegrationSpecification() {

    @Test
    fun `should implement declared interfaces`() {
        database.insert(TaggedTable) { it[TaggedTable.label] = "tag" }.map { it[TaggedTable.id] }.first()

        val entity = database.select(TaggedTable).map { it.toTagged() }.first()

        assertThat(entity).isInstanceOf(HasLabel::class.java)
        assertThat((entity as HasLabel).label).isEqualTo("tag")
    }

    @Test
    fun `should map a column onto an existing enum`() {
        database.insert(TaggedTable) { it[TaggedTable.label] = "tag"; it[TaggedTable.priority] = Priority.HIGH }.map { it[TaggedTable.id] }.first()

        val entity = database.select(TaggedTable).map { it.toTagged() }.first()

        val priority: Priority? = entity.priority // compiles only if the column references our hand-written enum
        assertThat(priority).isEqualTo(Priority.HIGH)
    }

}

