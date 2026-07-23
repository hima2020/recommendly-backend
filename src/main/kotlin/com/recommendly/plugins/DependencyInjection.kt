package com.recommendly.plugins

import com.recommendly.api.auth.AuthRepository
import com.recommendly.api.auth.AuthService
import com.recommendly.common.cache.RedisFactory
import com.recommendly.common.config.AppConfig
import com.recommendly.common.security.JwtService
import com.recommendly.common.security.PasswordService
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
 */
fun Application.configureDependencyInjection() {
    install(Koin) {
        slf4jLogger()
        modules(
            infrastructureModule,
            authModule,
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

/**
 * Authentication module — services and repositories for register/login/refresh/logout.
 */
val authModule = module {
    // AppConfig already in DI, extract JwtConfig from it
    single { PasswordService() }
    single { JwtService(get<AppConfig>().jwt) }
    single { AuthRepository() }
    single { AuthService(get(), get(), get()) }
}
