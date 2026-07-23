package com.recommendly

import com.recommendly.plugins.*
import io.ktor.server.application.*

/**
 * Entry point — Ktor calls this function automatically via EngineMain.
 * We wire every plugin here in a deliberate order:
 *   1. DI first  — so all services/repos are available to everything below
 *   2. DB next   — connections established before routes need them
 *   3. Plugins   — serialization, security, error handling
 *   4. Routing   — last, so all dependencies are ready
 */
fun Application.module() {
    configureDependencyInjection()
    configureDatabase()
    configureSerialization()
    configureHTTP()
    configureSecurity(
        // Extract JwtConfig from the already-loaded AppConfig in Koin
        jwtConfig = org.koin.ktor.ext.get<com.recommendly.common.config.AppConfig>().jwt
    )
    configureStatusPages()
    configureRouting()
}
