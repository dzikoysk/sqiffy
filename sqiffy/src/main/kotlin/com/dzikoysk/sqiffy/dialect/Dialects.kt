package com.dzikoysk.sqiffy.dialect

import com.dzikoysk.sqiffy.SqiffyDatabase
import com.dzikoysk.sqiffy.SqiffyDatabaseConfig
import com.dzikoysk.sqiffy.SqiffyLogger
import com.dzikoysk.sqiffy.StdoutSqiffyLogger
import com.dzikoysk.sqiffy.changelog.ChangelogBuilder
import com.dzikoysk.sqiffy.changelog.generator.dialects.MySqlSchemeGenerator
import com.dzikoysk.sqiffy.changelog.generator.dialects.SqliteSchemeGenerator
import com.dzikoysk.sqiffy.definition.NamingStrategy.RAW
import com.dzikoysk.sqiffy.dialect.Dialect.MYSQL
import com.dzikoysk.sqiffy.dialect.Dialect.SQLITE
import com.dzikoysk.sqiffy.dsl.generator.dialects.MySqlQueryGenerator
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
                    dialect = MYSQL,
                    sqlQueryGenerator = MySqlQueryGenerator,
                    changelogBuilder = ChangelogBuilder(MySqlSchemeGenerator, RAW)
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
        ): SqliteDatabase {
            return SqliteDatabase(
                state = SqiffyDatabaseConfig(
                    logger = logger,
                    dataSource = dataSource,
                    localJdbi = createGenericJdbi(dataSource).also { it.registerArgument(UUIDArgumentFactory()) },
                    dialect = SQLITE,
                    sqlQueryGenerator = SqliteQueryGenerator,
                    changelogBuilder = ChangelogBuilder(SqliteSchemeGenerator, RAW)
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