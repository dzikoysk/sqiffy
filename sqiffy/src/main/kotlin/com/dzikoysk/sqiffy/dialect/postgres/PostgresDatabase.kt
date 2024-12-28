@file:Suppress("internal", "INVISIBLE_REFERENCE", "INVISIBLE_MEMBER")

package com.dzikoysk.sqiffy.dialect.postgres

import com.dzikoysk.sqiffy.SqiffyDatabase
import com.dzikoysk.sqiffy.SqiffyDatabaseConfig
import com.dzikoysk.sqiffy.SqiffyLogger
import com.dzikoysk.sqiffy.StdoutSqiffyLogger
import com.dzikoysk.sqiffy.changelog.ChangelogBuilder
import com.dzikoysk.sqiffy.changelog.generator.dialects.PostgreSqlSchemeGenerator
import com.dzikoysk.sqiffy.definition.NamingStrategy.RAW
import com.dzikoysk.sqiffy.dialect.Dialect.POSTGRESQL
import com.dzikoysk.sqiffy.dialect.createGenericJdbi
import com.dzikoysk.sqiffy.dsl.Column
import com.dzikoysk.sqiffy.dsl.Table
import com.dzikoysk.sqiffy.dsl.TableWithAutogeneratedKey
import com.dzikoysk.sqiffy.dsl.generator.dialects.PostgreSqlQueryGenerator
import com.zaxxer.hikari.HikariDataSource
import org.jdbi.v3.postgres.PostgresPlugin

import kotlin.internal.LowPriorityInOverloadResolution

class PostgresDatabase(state: SqiffyDatabaseConfig) : SqiffyDatabase(state) {

    companion object {
        fun createPostgresDatabase(
            logger: SqiffyLogger = StdoutSqiffyLogger(),
            dataSource: HikariDataSource
        ): PostgresDatabase {
            return PostgresDatabase(
                state = SqiffyDatabaseConfig(
                    logger = logger,
                    dataSource = dataSource,
                    localJdbi = createGenericJdbi(dataSource).also {
                        it.installPlugin(PostgresPlugin())
                    },
                    dialect = POSTGRESQL,
                    sqlQueryGenerator = PostgreSqlQueryGenerator,
                    changelogBuilder = ChangelogBuilder(PostgreSqlSchemeGenerator, RAW)
                )
            )
        }
    }

    @LowPriorityInOverloadResolution
    fun upsert(
        table: TableWithAutogeneratedKey<*>,
        insertValues: InsertValuesBody? = null,
        updateValues: UpdateValuesBody? = null
    ): UpsertStatement =
        UpsertStatement(
            database = getDatabase(),
            handleAccessor = getHandleAccessor(),
            table = table,
            insertValuesSupplier = insertValues,
            updateValuesSupplier = updateValues
        )

    fun <T : Table> upsert(
        table: T,
        conflictingColumns: (T) -> Collection<Column<*>>,
    ): UpsertStatement =
        upsert(
            table = table,
            insertValues = null,
            updateValues = null,
            conflictingColumns = conflictingColumns
        )

    @LowPriorityInOverloadResolution
    fun <T : Table> upsert(
        table: T,
        conflictingColumns: T.() -> Collection<Column<*>>,
        insertValues: InsertValuesBody? = null,
        updateValues: UpdateValuesBody? = null,
    ): UpsertStatement =
        UpsertStatement(
            database = getDatabase(),
            handleAccessor = getHandleAccessor(),
            table = table,
            insertValuesSupplier = insertValues,
            updateValuesSupplier = updateValues,
            conflictingColumns = conflictingColumns(table),
        )

}