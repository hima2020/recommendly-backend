package com.recommendly.api.health

import com.recommendly.common.cache.RedisFactory
import io.ktor.http.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.Serializable
import mu.KotlinLogging
import org.jetbrains.exposed.sql.transactions.transaction

private val logger = KotlinLogging.logger {}

/**
 * Health check endpoints.
 *
 * Dependencies are passed as parameters (injected at Application level in Routing.kt)
 * to avoid Koin Route-level injection issues with Ktor 3.x.
 *
 * GET /api/v1/health        — liveness (is the server running?)
 * GET /api/v1/health/ready  — readiness (DB + Redis connected?)
 */
fun Route.healthRoutes(redis: RedisFactory) {

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
                transaction {
                    exec("SELECT 1")
                }
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
            call.respond(
                status,
                ReadinessResponse(
                    status = if (allHealthy) "UP" else "DEGRADED",
                    checks = checks
                )
            )
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
