package com.dzikoysk.sqiffy.e2e.specification

import com.dzikoysk.sqiffy.GuildDefinition
import com.dzikoysk.sqiffy.Slf4JSqiffyLogger
import com.dzikoysk.sqiffy.Sqiffy
import com.dzikoysk.sqiffy.SqiffyDatabase
import com.dzikoysk.sqiffy.UserDefinition
import com.zaxxer.hikari.HikariDataSource
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.slf4j.LoggerFactory

abstract class SqiffyE2ETestSpecification {

    lateinit var database: SqiffyDatabase

    abstract fun createDataSource(): HikariDataSource

    @BeforeEach
    fun setup() {
        this.database = Sqiffy.createDatabase(
            dataSource = createDataSource(),
            logger = Slf4JSqiffyLogger(LoggerFactory.getLogger(SqiffyDatabase::class.java))
        )
        val changeLog = database.generateChangeLog(UserDefinition::class, GuildDefinition::class)
        database.runMigrations(changeLog = changeLog)
    }

    @AfterEach
    fun tearDown() {
        database.close()
    }

}