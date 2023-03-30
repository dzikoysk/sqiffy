package com.dzikoysk.sqiffy.e2e.specification

import com.dzikoysk.sqiffy.GuildDefinition
import com.dzikoysk.sqiffy.Slf4JSqiffyLogger
import com.dzikoysk.sqiffy.Sqiffy
import com.dzikoysk.sqiffy.UserDefinition
import com.zaxxer.hikari.HikariDataSource
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.slf4j.LoggerFactory

abstract class SqiffyE2ETestSpecification {

    lateinit var sqiffy: Sqiffy

    abstract fun createDataSource(): HikariDataSource

    @BeforeEach
    fun setup() {
        this.sqiffy = Sqiffy(
            dataSource = createDataSource(),
            logger = Slf4JSqiffyLogger(LoggerFactory.getLogger(Sqiffy::class.java))
        )
        val changeLog = sqiffy.generateChangeLog(UserDefinition::class, GuildDefinition::class)
        sqiffy.runMigrations(changeLog = changeLog)
    }

    @AfterEach
    fun tearDown() {
        sqiffy.close()
    }

}