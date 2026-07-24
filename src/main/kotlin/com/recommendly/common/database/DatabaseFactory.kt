package com.recommendly.common.database

import com.recommendly.common.config.DatabaseConfig
import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import io.ktor.server.application.*
import mu.KotlinLogging
import org.flywaydb.core.Flyway
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.transactions.transaction

private val logger = KotlinLogging.logger {}

/**
 * Manages the PostgreSQL connection lifecycle.
 *
 * Three layers here:
 * 1. HikariCP — connection pool (reuses connections, handles timeouts)
 * 2. Flyway   — runs SQL migration scripts in order at startup
 * 3. Exposed  — connects the ORM to the HikariCP pool
 *
 * Why a connection pool?
 * Opening a raw TCP connection to PostgreSQL is expensive (~50ms).
 * HikariCP maintains a warm pool of connections that are reused instantly.
 * Without it, every API request would pay that 50ms penalty.
 */
object DatabaseFactory {

    fun init(config: DatabaseConfig) {
        logger.info { "Initialising database connection pool..." }

        val dataSource = buildHikariDataSource(config)

        logger.info { "Running Flyway migrations..." }
        runMigrations(dataSource)

        logger.info { "Connecting Exposed ORM..." }
        Database.connect(dataSource)

        logger.info { "Database ready ✓" }
    }

    private fun buildHikariDataSource(config: DatabaseConfig): HikariDataSource {
        val hikariConfig = HikariConfig().apply {
            jdbcUrl = config.url
            username = config.user
            password = config.password
            driverClassName = "org.postgresql.Driver"
            maximumPoolSize = config.poolSize
            minimumIdle = 2
            idleTimeout = 300_000          // 5 minutes
            connectionTimeout = 30_000     // 30 seconds
            maxLifetime = 1_800_000        // 30 minutes
            poolName = "RecommendlyPool"
            isAutoCommit = false
            transactionIsolation = "TRANSACTION_REPEATABLE_READ"
            validate()
        }
        return HikariDataSource(hikariConfig)
    }

    private fun runMigrations(dataSource: HikariDataSource) {
        // Use filesystem: path so Flyway reads SQL files directly from disk.
        // classpath: scanning inside fat JARs can fail the filename convention
        // check even with correctly-named files (Flyway 10 known quirk).
        // The Dockerfile copies migration files to /app/db/migration at build time.
        val migrationsPath = System.getenv("FLYWAY_MIGRATIONS_PATH")
            ?: "filesystem:/app/db/migration"

        Flyway.configure()
            .dataSource(dataSource)
            .locations(migrationsPath)
            .baselineOnMigrate(true)
            .baselineVersion("0")
            .load()
            .migrate()
    }
}

/**
 * Extension function that wraps Exposed transactions in a Ktor-friendly way.
 * Use this in every repository function instead of calling transaction {} directly.
 */
suspend fun <T> dbQuery(block: () -> T): T =
    kotlinx.coroutines.Dispatchers.IO.run {
        transaction { block() }
    }
