package com.dzikoysk.sqiffy

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.transactions.transactionManager
import org.jetbrains.exposed.sql.vendors.H2Dialect
import java.sql.Connection.TRANSACTION_SERIALIZABLE

data class DatabaseConnection(
    val databaseSource: HikariDataSource,
    val database: Database
) : AutoCloseable {

    override fun close() =
        databaseSource.close()

}

fun createHikariDataSource(driver: String, url: String, threadPool: Int, username: String? = null, password: String? = null): HikariDataSource =
    HikariDataSource(
        HikariConfig().apply {
            this.jdbcUrl = url
            this.driverClassName = driver
            this.maximumPoolSize = threadPool
            username?.also { this.username = it }
            password?.also { this.password = it }
        }
    )

fun HikariDataSource.toDatabaseConnection(): DatabaseConnection {
    Database.registerDialect("h2") {
        object : H2Dialect() {
            override val needsQuotesWhenSymbolsInNames: Boolean
                get() = true
        }
    }

    val database = Database.connect(this)
    database.transactionManager.defaultIsolationLevel = TRANSACTION_SERIALIZABLE

    return DatabaseConnection(
        databaseSource = this,
        database = database
    )
}