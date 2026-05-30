package com.dzikoysk.sqiffy.specification

import com.dzikoysk.sqiffy.shared.H2Mode
import com.dzikoysk.sqiffy.shared.createH2DataSource
import com.dzikoysk.sqiffy.shared.createHikariDataSource
import com.dzikoysk.sqiffy.shared.createSQLiteDataSource
import com.zaxxer.hikari.HikariDataSource
import org.junit.jupiter.api.extension.BeforeEachCallback
import org.junit.jupiter.api.extension.ExtensionContext
import org.testcontainers.containers.MariaDBContainer
import org.testcontainers.containers.MySQLContainer
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.utility.DockerImageName

internal abstract class SqiffyTargetExtension : BeforeEachCallback {

    abstract fun createDataSource(): HikariDataSource

    override fun beforeEach(context: ExtensionContext) {
        val instance = context.requiredTestInstance
        val type = instance.javaClass
        type.getField("_dataSource").set(instance, createDataSource())
        type.getField("_extensionInitialized").setBoolean(instance, true)
    }

}

internal class H2Target : SqiffyTargetExtension() {
    override fun createDataSource(): HikariDataSource = createH2DataSource(H2Mode.MYSQL)
}

internal class SqliteTarget : SqiffyTargetExtension() {
    override fun createDataSource(): HikariDataSource = createSQLiteDataSource()
}

internal class PostgresTarget : SqiffyTargetExtension() {
    override fun createDataSource(): HikariDataSource =
        createHikariDataSource("org.postgresql.Driver", CONTAINER.jdbcUrl, username = CONTAINER.username, password = CONTAINER.password)

    private class Container(image: String) : PostgreSQLContainer<Container>(DockerImageName.parse(image))
    companion object {
        private val CONTAINER by lazy { Container("postgres:11.12").also { it.start() } }
    }
}

internal class MySqlTarget : SqiffyTargetExtension() {
    override fun createDataSource(): HikariDataSource =
        createHikariDataSource("com.mysql.cj.jdbc.Driver", CONTAINER.jdbcUrl, username = CONTAINER.username, password = CONTAINER.password)

    private class Container(image: String) : MySQLContainer<Container>(DockerImageName.parse(image))
    companion object {
        private val CONTAINER by lazy { Container("mysql:8.0.25").also { it.start() } }
    }
}

internal class MariaDbTarget : SqiffyTargetExtension() {
    override fun createDataSource(): HikariDataSource =
        createHikariDataSource("org.mariadb.jdbc.Driver", CONTAINER.jdbcUrl, username = CONTAINER.username, password = CONTAINER.password)

    private class Container(image: String) : MariaDBContainer<Container>(DockerImageName.parse(image))
    companion object {
        private val CONTAINER by lazy { Container("mariadb:10.6.1").also { it.start() } }
    }
}
