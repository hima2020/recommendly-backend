package com.recommendly.plugins

import com.recommendly.api.health.healthRoutes
import com.recommendly.common.cache.RedisFactory
import io.ktor.server.application.*
import io.ktor.server.routing.*
import org.koin.ktor.ext.inject

/**
 * Central routing registry.
 *
 * Dependencies are injected at the Application level (where Koin works reliably
 * in Ktor 3.x) and passed down to route functions as parameters.
 * This avoids the Koin Route-level injection issue with Ktor 3.x.
 */
fun Application.configureRouting() {
    val redis by inject<RedisFactory>()

    routing {
        route("/api/v1") {
            healthRoutes(redis)
            // authRoutes()    ← Phase 2
            // userRoutes()    ← Phase 2
            // stockRoutes()   ← Phase 3
        }
    }
}
