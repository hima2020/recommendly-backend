package com.recommendly.plugins

import com.recommendly.common.config.AppConfig
import com.recommendly.common.database.DatabaseFactory
import io.ktor.server.application.*
import org.koin.ktor.ext.inject

/**
 * Initialises the database as a Ktor plugin.
 * Runs Flyway migrations and establishes the Exposed connection.
 */
fun Application.configureDatabase() {
    val config by inject<AppConfig>()
    DatabaseFactory.init(config.database)
}
