package com.recommendly.plugins

import com.recommendly.common.cache.RedisFactory
import com.recommendly.common.config.AppConfig
import io.ktor.server.application.*
import org.koin.dsl.module
import org.koin.ktor.plugin.Koin
import org.koin.logger.slf4jLogger

/**
 * Configures Koin for dependency injection.
 *
 * Why Koin over Dagger/Hilt?
 * - Kotlin-first DSL, no annotation processing
 * - Works perfectly in Ktor (no Android dependency)
 * - Simple to read and test
 * - Koin 4.x has native Ktor 3.x support
 *
 * As we add features (auth, users, etc.), we add their Koin modules here.
 */
fun Application.configureDependencyInjection() {
    install(Koin) {
        slf4jLogger()
        modules(
            infrastructureModule,
            // authModule,    ← added in Phase 2
            // usersModule,   ← added in Phase 2
        )
    }
}

/**
 * Core infrastructure module — shared across all features.
 */
val infrastructureModule = module {
    single { AppConfig.load() }
    single { RedisFactory.create(get()) }
}
