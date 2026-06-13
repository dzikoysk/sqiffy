package com.dzikoysk.sqiffy.specification

import com.dzikoysk.sqiffy.Slf4JSqiffyLogger
import com.dzikoysk.sqiffy.Sqiffy
import com.dzikoysk.sqiffy.SqiffyDatabase
import com.dzikoysk.sqiffy.e2e.GuildDefinition
import com.dzikoysk.sqiffy.e2e.GuildTable
import com.dzikoysk.sqiffy.e2e.UserDefinition
import com.dzikoysk.sqiffy.infra.UserTable
import com.dzikoysk.sqiffy.migrator.SqiffyMigrator
import com.zaxxer.hikari.HikariDataSource
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.slf4j.LoggerFactory

@Suppress("PropertyName")
internal abstract class IntegrationSpecification {

    @JvmField var _extensionInitialized = false
    @JvmField var _dataSource: HikariDataSource? = null

    lateinit var database: SqiffyDatabase<*>

    @BeforeEach
    fun bootDatabase() {
        check(_extensionInitialized) { "Missing target extension - annotate the concrete test with @ExtendWith(<Target>::class)" }

        database = Sqiffy.createDatabase(
            dataSource = _dataSource!!,
            logger = Slf4JSqiffyLogger(LoggerFactory.getLogger(SqiffyDatabase::class.java))
        )

        database.runMigrations(
            SqiffyMigrator(
                database.generateChangeLog(
                    tables = listOf(
                        UserDefinition::class,
                        GuildDefinition::class,
                        TypesDefinition::class,
                        LinkDefinition::class,
                        TaggedDefinition::class,
                    )
                )
            )
        )

        // wipe children before parents (guilds.owner -> users FK)
        database.delete(GuildTable).execute()
        database.delete(TypesTable).execute()
        database.delete(LinkTable).execute()
        database.delete(TaggedTable).execute()
        database.delete(UserTable).execute()
    }

    @AfterEach
    fun closeDatabase() {
        database.close()
    }

}
