package com.dzikoysk.sqiffy.shared

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import java.io.File
import java.nio.file.Path
import kotlin.io.path.absolutePathString

fun createHikariDataSource(
    driver: String,
    url: String,
    threadPool: Int = 1,
    username: String? = null,
    password: String? = null
): HikariDataSource =
    HikariDataSource(
        HikariConfig().apply {
            this.jdbcUrl = url
            this.driverClassName = driver
            this.maximumPoolSize = threadPool
            username?.also { this.username = it }
            password?.also { this.password = it }
        }
    )

fun createTestDatabaseFile(name: String): Path =
    File.createTempFile(name, ".db")
        .also { it.deleteOnExit() }
        .toPath()

enum class H2Mode {
    MYSQL
}

fun createH2DataSource(mode: H2Mode, path: Path = createTestDatabaseFile("test-database")): HikariDataSource =
    createHikariDataSource(
        driver = "org.h2.Driver",
        url = "jdbc:h2:${path.absolutePathString()};MODE=${mode.name}"
    )

fun createSQLiteDataSource(path: Path = createTestDatabaseFile("test-database")): HikariDataSource =
    createHikariDataSource(
        driver = "org.sqlite.JDBC",
        url = "jdbc:sqlite:${path.absolutePathString()}"
    )