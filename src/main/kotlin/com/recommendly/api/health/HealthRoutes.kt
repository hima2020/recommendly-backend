package com.recommendly.api.health

import com.recommendly.common.cache.RedisFactory
import io.ktor.http.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.Serializable
import mu.KotlinLogging
import org.jetbrains.exposed.sql.transactions.transaction
import org.koin.ktor.ext.inject

private val logger = KotlinLogging.logger {}

/**
 * Health check endpoints — the first routes we build and the last we remove.
 *
 * Why health checks matter in production:
 * - Docker uses them to know when a container is ready to serve traffic
 * - Load balancers use them to route away from unhealthy instances
 * - Monitoring systems (Grafana, etc.) alert when they fail
 * - GitHub Actions deployment waits for a healthy response before finishing
 *
 * Two endpoints:
 * GET /api/v1/health        — quick liveness check (is the server running?)
 * GET /api/v1/health/ready  — full readiness check (DB + Redis connected?)
 */
fun Route.healthRoutes() {
    val redis by inject<RedisFactory>()

    route("/health") {

        // Liveness — confirms the HTTP server is up
        get {
            call.respond(HttpStatusCode.OK, HealthResponse(status = "UP"))
        }

        // Readiness — confirms all dependencies are connected
        get("/ready") {
            val checks = mutableMapOf<String, String>()
            var allHealthy = true

            // Check PostgreSQL
            try {
                transaction { /* simple connection test */ }
                checks["database"] = "UP"
            } catch (e: Exception) {
                logger.error(e) { "Database health check failed" }
                checks["database"] = "DOWN"
                allHealthy = false
            }

            // Check Redis
            try {
                val pong = redis.commands.ping()
                checks["redis"] = if (pong == "PONG") "UP" else "DOWN"
                if (pong != "PONG") allHealthy = false
            } catch (e: Exception) {
                logger.error(e) { "Redis health check failed" }
                checks["redis"] = "DOWN"
                allHealthy = false
            }

            val status = if (allHealthy) HttpStatusCode.OK else HttpStatusCode.ServiceUnavailable
            call.respond(status, ReadinessResponse(
                status = if (allHealthy) "UP" else "DEGRADED",
                checks = checks
            ))
        }
    }
}

@Serializable
data class HealthResponse(val status: String)

@Serializable
data class ReadinessResponse(
    val status: String,
    val checks: Map<String, String>
)
