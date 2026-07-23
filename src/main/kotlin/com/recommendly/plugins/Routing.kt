package com.recommendly.plugins

import com.recommendly.api.auth.AuthService
import com.recommendly.api.auth.authRoutes
import com.recommendly.api.health.healthRoutes
import com.recommendly.common.cache.RedisFactory
import io.ktor.server.application.*
import io.ktor.server.routing.*
import org.koin.ktor.ext.inject

/**
 * Central routing registry.
 *
 * All dependencies are injected at the Application level (where Koin works reliably
 * in Ktor 3.x) and passed down to route functions as parameters.
 * Never use `val x by inject<>()` inside a Route extension function.
 */
fun Application.configureRouting() {
    val redis       by inject<RedisFactory>()
    val authService by inject<AuthService>()

    routing {
        route("/api/v1") {
            healthRoutes(redis)
            authRoutes(authService)
            // userRoutes()    ← Phase 3
            // stockRoutes()   ← Phase 3
        }
    }
}
