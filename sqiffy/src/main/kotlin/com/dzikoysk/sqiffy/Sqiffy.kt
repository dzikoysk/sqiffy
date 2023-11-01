package com.dzikoysk.sqiffy

import com.zaxxer.hikari.HikariDataSource

object Sqiffy {

    @Suppress("UNCHECKED_CAST")
    fun <DATABASE : SqiffyDatabase> createDatabase(
        logger: SqiffyLogger = StdoutSqiffyLogger(),
        dataSource: HikariDataSource
    ): DATABASE =
        when {
            dataSource.jdbcUrl.contains("mysql", ignoreCase = true) -> MySqlDatabase.createMySQLDatabase(logger, dataSource)
            dataSource.jdbcUrl.contains("mariadb", ignoreCase = true) -> MySqlDatabase.createMySQLDatabase(logger, dataSource)
            dataSource.jdbcUrl.contains("postgresql", ignoreCase = true) -> PostgresDatabase.createPostgresDatabase(logger, dataSource)
            dataSource.jdbcUrl.contains("sqlite", ignoreCase = true) -> SqliteDatabase.createSqliteDatabase(logger, dataSource)
            else -> throw IllegalArgumentException("Unsupported dialect for ${dataSource.jdbcUrl}")
        } as DATABASE

}

