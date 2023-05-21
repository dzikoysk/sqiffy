package com.dzikoysk.sqiffy

import com.dzikoysk.sqiffy.changelog.ChangeLog
import com.dzikoysk.sqiffy.changelog.ChangeLogGenerator
import com.dzikoysk.sqiffy.changelog.Migrator
import com.dzikoysk.sqiffy.changelog.Migrator.SqiffyMetadataTable
import com.dzikoysk.sqiffy.changelog.MySqlSchemeGenerator
import com.dzikoysk.sqiffy.changelog.PostgreSqlSchemeGenerator
import com.dzikoysk.sqiffy.changelog.SqlSchemeGenerator
import com.dzikoysk.sqiffy.definition.RuntimeTypeFactory
import com.dzikoysk.sqiffy.dsl.Table
import com.dzikoysk.sqiffy.dsl.Values
import com.dzikoysk.sqiffy.dsl.generator.MySqlQueryGenerator
import com.dzikoysk.sqiffy.dsl.generator.PostgreSqlQueryGenerator
import com.dzikoysk.sqiffy.dsl.generator.SqlQueryGenerator
import com.dzikoysk.sqiffy.dsl.statements.DeleteStatement
import com.dzikoysk.sqiffy.dsl.statements.InsertStatement
import com.dzikoysk.sqiffy.dsl.statements.SelectStatement
import com.dzikoysk.sqiffy.dsl.statements.UpdateStatement
import com.dzikoysk.sqiffy.shared.UUIDArgumentFactory
import com.zaxxer.hikari.HikariDataSource
import org.jdbi.v3.core.Jdbi
import org.jdbi.v3.core.kotlin.KotlinPlugin
import org.jdbi.v3.core.statement.SqlStatements
import org.jdbi.v3.jackson2.Jackson2Plugin
import org.jdbi.v3.sqlobject.SqlObjectPlugin
import org.jdbi.v3.sqlobject.kotlin.KotlinSqlObjectPlugin
import java.io.Closeable
import kotlin.reflect.KClass


enum class Dialect  {
    MYSQL,
    POSTGRESQL
}

object Sqiffy {

    fun createDatabase(
        logger: SqiffyLogger = StdoutSqiffyLogger(),
        dataSource: HikariDataSource
    ): SqiffyDatabase {
        val dialect = when {
            dataSource.jdbcUrl.contains("mysql", ignoreCase = true) -> Dialect.MYSQL
            dataSource.jdbcUrl.contains("mariadb", ignoreCase = true) -> Dialect.MYSQL
            dataSource.jdbcUrl.contains("postgresql", ignoreCase = true) -> Dialect.POSTGRESQL
            else -> throw IllegalArgumentException("Unsupported dialect for ${dataSource.jdbcUrl}")
        }

        val localJdbi = Jdbi.create(dataSource)
            .installPlugin(SqlObjectPlugin())
            .installPlugin(Jackson2Plugin())
            .installPlugin(KotlinPlugin())
            .installPlugin(KotlinSqlObjectPlugin())
            .also {
                it.configure(SqlStatements::class.java) { statements -> statements.isUnusedBindingAllowed = false }
            }
            .also {
                when (dialect) {
                    Dialect.MYSQL -> it.registerArgument(UUIDArgumentFactory())
                    Dialect.POSTGRESQL -> {}
                }
            }

        val sqlSchemeGenerator: SqlSchemeGenerator = when (dialect) {
            Dialect.MYSQL -> MySqlSchemeGenerator
            Dialect.POSTGRESQL -> PostgreSqlSchemeGenerator
        }

        val sqlQueryGenerator: SqlQueryGenerator = when (dialect) {
            Dialect.MYSQL -> MySqlQueryGenerator
            Dialect.POSTGRESQL -> PostgreSqlQueryGenerator
        }

        val changeLogGenerator = ChangeLogGenerator(
            sqlSchemeGenerator = sqlSchemeGenerator,
            typeFactory = RuntimeTypeFactory()
        )

        return SqiffyDatabase(
            logger = logger,
            dialect = dialect,
            dataSource = dataSource,
            sqlSchemeGenerator = sqlSchemeGenerator,
            sqlQueryGenerator = sqlQueryGenerator,
            localJdbi = localJdbi,
            changeLogGenerator = changeLogGenerator
        )
    }

}

open class SqiffyDatabase(
    val logger: SqiffyLogger = StdoutSqiffyLogger(),
    val dialect: Dialect,
    val dataSource: HikariDataSource,
    val sqlSchemeGenerator: SqlSchemeGenerator,
    val sqlQueryGenerator: SqlQueryGenerator,
    private val localJdbi: Jdbi,
    private val changeLogGenerator: ChangeLogGenerator,
) : Closeable {

    fun generateChangeLog(vararg classes: KClass<*>): ChangeLog =
        changeLogGenerator.generateChangeLog(*classes)

    fun runMigrations(metadataTable: SqiffyMetadataTable = SqiffyMetadataTable(), changeLog: ChangeLog) =
        Migrator(this).runMigrations(metadataTable, changeLog)

    fun select(table: Table): SelectStatement =
        SelectStatement(this, table)

    fun insert(table: Table, values: (Values) -> Unit): InsertStatement =
        InsertStatement(this, table, Values().also { values.invoke(it) })

    fun update(table: Table, values: (Values) -> Unit): UpdateStatement =
        UpdateStatement(this, table, Values().also { values.invoke(it) })

    fun delete(table: Table): DeleteStatement =
        DeleteStatement(this, table)

    fun getJdbi(): Jdbi =
        localJdbi

    override fun close() {
        dataSource.close()
    }

}
