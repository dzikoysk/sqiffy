package com.dzikoysk.sqiffy

import com.dzikoysk.sqiffy.changelog.Changelog
import com.dzikoysk.sqiffy.changelog.ChangelogBuilder
import com.dzikoysk.sqiffy.changelog.generator.SqlSchemeGenerator
import com.dzikoysk.sqiffy.changelog.generator.dialects.getSchemeGenerator
import com.dzikoysk.sqiffy.dsl.DslHandle
import com.dzikoysk.sqiffy.dsl.JdbiDslHandle
import com.dzikoysk.sqiffy.dsl.generator.SqlQueryGenerator
import com.dzikoysk.sqiffy.migrator.Migrator
import com.dzikoysk.sqiffy.transaction.HandleAccessor
import com.dzikoysk.sqiffy.transaction.JdbiTransaction
import com.dzikoysk.sqiffy.transaction.StandardHandleAccessor
import com.dzikoysk.sqiffy.transaction.Transaction
import com.dzikoysk.sqiffy.transaction.TransactionManager
import com.zaxxer.hikari.HikariDataSource
import java.io.Closeable
import kotlin.reflect.KClass
import kotlin.reflect.KProperty
import org.jdbi.v3.core.Jdbi

data class SqiffyDatabaseConfig(
    val logger: SqiffyLogger = StdoutSqiffyLogger(),
    val dialect: Dialect,
    val dataSource: HikariDataSource,
    val sqlSchemeGenerator: SqlSchemeGenerator = dialect.getSchemeGenerator(),
    val sqlQueryGenerator: SqlQueryGenerator,
    val localJdbi: Jdbi,
    val changelogBuilder: ChangelogBuilder,
    val handleAccessor: HandleAccessor = StandardHandleAccessor(localJdbi),
)

abstract class SqiffyDatabase(state: SqiffyDatabaseConfig) : DslHandle(), TransactionManager, Closeable {

    internal val logger: SqiffyLogger = state.logger
    internal val dialect: Dialect = state.dialect
    internal val dataSource: HikariDataSource = state.dataSource
    internal val sqlSchemeGenerator: SqlSchemeGenerator = state.sqlSchemeGenerator
    internal val sqlQueryGenerator: SqlQueryGenerator = state.sqlQueryGenerator
    internal val localJdbi: Jdbi = state.localJdbi
    private val changelogBuilder: ChangelogBuilder = state.changelogBuilder
    private val handleAccessor = state.handleAccessor

    override fun close() {
        dataSource.close()
    }

    fun generateChangeLog(tables: List<KClass<*>>, functions: List<KProperty<*>> = emptyList()): Changelog =
        changelogBuilder.generateChangeLogAtRuntime(tables = tables, functions = functions)

    fun <RESULT> runMigrations(migrator: Migrator<RESULT>): RESULT =
        migrator.runMigrations(this)

    override fun <T> transaction(block: (Transaction) -> T): T {
        return localJdbi.inTransaction<T, Exception> { handle ->
            block.invoke(JdbiTransaction(handle))
        }
    }

    fun with(transaction: Transaction?): DslHandle =
        when (transaction) {
            is JdbiTransaction -> JdbiDslHandle(this, transaction.handle)
            else -> this
        }

    fun getJdbi(): Jdbi =
        localJdbi

    fun getDialect(): Dialect =
        dialect

    override fun getHandleAccessor(): HandleAccessor =
        handleAccessor

    override fun getDatabase(): SqiffyDatabase =
        this

}