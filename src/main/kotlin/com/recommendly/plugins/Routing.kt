package com.recommendly.plugins

import com.recommendly.api.health.healthRoutes
import io.ktor.server.application.*
import io.ktor.server.routing.*

/**
 * Central routing registry.
 *
 * Every feature registers its routes here under the /api/v1 prefix.
 * This gives us:
 * - API versioning from day one (easy to add /api/v2 later)
 * - A single file to see all available endpoints at a glance
 */
fun Application.configureRouting() {
    routing {
        route("/api/v1") {
            healthRoutes()
            // authRoutes()    ← Phase 2
            // userRoutes()    ← Phase 2
            // stockRoutes()   ← Phase 3
        }
    }
}
