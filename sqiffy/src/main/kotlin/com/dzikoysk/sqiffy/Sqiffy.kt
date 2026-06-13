package com.dzikoysk.sqiffy

import com.dzikoysk.sqiffy.dialect.MySqlDatabase
import com.dzikoysk.sqiffy.dialect.SqliteDatabase
import com.dzikoysk.sqiffy.dialect.postgres.PostgresDatabase
import com.zaxxer.hikari.HikariDataSource

object Sqiffy {

    fun createDatabase(
        logger: SqiffyLogger = StdoutSqiffyLogger(),
        dataSource: HikariDataSource
    ): SqiffyDatabase<*> =
        when {
            dataSource.jdbcUrl.contains("mysql", ignoreCase = true) -> MySqlDatabase.createMySQLDatabase(logger, dataSource)
            dataSource.jdbcUrl.contains("mariadb", ignoreCase = true) -> MySqlDatabase.createMySQLDatabase(logger, dataSource)
            dataSource.jdbcUrl.contains("postgresql", ignoreCase = true) -> PostgresDatabase.createPostgresDatabase(logger, dataSource)
            dataSource.jdbcUrl.contains("sqlite", ignoreCase = true) -> SqliteDatabase.createSqliteDatabase(logger, dataSource)
            else -> throw IllegalArgumentException("Unsupported dialect for ${dataSource.jdbcUrl}")
        }

}
