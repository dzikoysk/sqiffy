package com.dzikoysk.sqiffy.shared

import com.dzikoysk.sqiffy.createHikariDataSource
import com.zaxxer.hikari.HikariDataSource
import java.io.File
import java.nio.file.Path
import kotlin.io.path.absolutePathString

// h2 utils:

fun createTestDatabaseFile(name: String): Path =
    File.createTempFile(name, ".db")
        .also { it.deleteOnExit() }
        .toPath()

enum class H2Mode {
    MYSQL,
    POSTGRESQL
}

fun createH2DataSource(mode: H2Mode): HikariDataSource =
    createHikariDataSource(
        driver = "org.h2.Driver",
        url = "jdbc:h2:${createTestDatabaseFile("test-database").absolutePathString()};MODE=${mode.name}"
    )