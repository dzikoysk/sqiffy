package com.dzikoysk.sqiffy

import com.dzikoysk.sqiffy.changelog.ChangelogBuilder
import com.dzikoysk.sqiffy.changelog.generator.dialects.MySqlSchemeGenerator
import com.dzikoysk.sqiffy.changelog.generator.dialects.PostgreSqlSchemeGenerator
import com.dzikoysk.sqiffy.changelog.generator.dialects.SqliteSchemeGenerator
import com.dzikoysk.sqiffy.dsl.generator.dialects.MySqlQueryGenerator
import com.dzikoysk.sqiffy.dsl.generator.dialects.PostgreSqlQueryGenerator
import com.dzikoysk.sqiffy.dsl.generator.dialects.SqliteQueryGenerator
import com.dzikoysk.sqiffy.shared.UUIDArgumentFactory
import com.zaxxer.hikari.HikariDataSource
import org.jdbi.v3.core.Jdbi
import org.jdbi.v3.core.kotlin.KotlinPlugin
import org.jdbi.v3.core.statement.SqlStatements
import org.jdbi.v3.jackson2.Jackson2Plugin
import org.jdbi.v3.sqlobject.SqlObjectPlugin
import org.jdbi.v3.sqlobject.kotlin.KotlinSqlObjectPlugin

enum class Dialect {
    MYSQL,
    POSTGRESQL,
    SQLITE
}

class PostgresDatabase(state: SqiffyDatabaseConfig) : SqiffyDatabase(state) {
    companion object {
        fun createPostgresDatabase(
            logger: SqiffyLogger = StdoutSqiffyLogger(),
            dataSource: HikariDataSource
        ): MySqlDatabase {
            return MySqlDatabase(
                state = SqiffyDatabaseConfig(
                    logger = logger,
                    dataSource = dataSource,
                    localJdbi = createGenericJdbi(dataSource),
                    dialect = Dialect.POSTGRESQL,
                    sqlQueryGenerator = PostgreSqlQueryGenerator,
                    changelogBuilder = ChangelogBuilder(PostgreSqlSchemeGenerator)
                )
            )
        }
    }
}

class MySqlDatabase(state: SqiffyDatabaseConfig) : SqiffyDatabase(state) {
    companion object {
        fun createMySQLDatabase(
            logger: SqiffyLogger = StdoutSqiffyLogger(),
            dataSource: HikariDataSource
        ): MySqlDatabase {
            return MySqlDatabase(
                state = SqiffyDatabaseConfig(
                    logger = logger,
                    dataSource = dataSource,
                    localJdbi = createGenericJdbi(dataSource).also { it.registerArgument(UUIDArgumentFactory()) },
                    dialect = Dialect.MYSQL,
                    sqlQueryGenerator = MySqlQueryGenerator,
                    changelogBuilder = ChangelogBuilder(MySqlSchemeGenerator)
                )
            )
        }
    }
}

class SqliteDatabase(state: SqiffyDatabaseConfig) : SqiffyDatabase(state) {
    companion object {
        fun createSqliteDatabase(
            logger: SqiffyLogger = StdoutSqiffyLogger(),
            dataSource: HikariDataSource
        ): MySqlDatabase {
            return MySqlDatabase(
                state = SqiffyDatabaseConfig(
                    logger = logger,
                    dataSource = dataSource,
                    localJdbi = createGenericJdbi(dataSource).also { it.registerArgument(UUIDArgumentFactory()) },
                    dialect = Dialect.SQLITE,
                    sqlQueryGenerator = SqliteQueryGenerator,
                    changelogBuilder = ChangelogBuilder(SqliteSchemeGenerator)
                )
            )
        }
    }
}

fun createGenericJdbi(dataSource: HikariDataSource): Jdbi =
    Jdbi.create(dataSource)
        .installPlugin(SqlObjectPlugin())
        .installPlugin(Jackson2Plugin())
        .installPlugin(KotlinPlugin())
        .installPlugin(KotlinSqlObjectPlugin())
        .also {
            it.configure(SqlStatements::class.java) { statements -> statements.isUnusedBindingAllowed = false }
        }