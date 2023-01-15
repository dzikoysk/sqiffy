package com.dzikoysk.sqiffy.exposed

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import org.jetbrains.exposed.sql.Database

data class DatabaseConnection(
    val databaseSource: HikariDataSource,
    val database: Database
) : AutoCloseable {

    override fun close() =
        databaseSource.close()

}

fun createDataSource(driver: String, url: String, threadPool: Int, username: String? = null, password: String? = null): HikariDataSource =
    HikariDataSource(
        HikariConfig().apply {
            this.jdbcUrl = url
            this.driverClassName = driver
            this.maximumPoolSize = threadPool
            username?.also { this.username = it }
            password?.also { this.password = it }
        }
    )
