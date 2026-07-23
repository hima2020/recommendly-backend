package com.recommendly.plugins

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.plugins.statuspages.*
import io.ktor.server.response.*
import kotlinx.serialization.Serializable
import mu.KotlinLogging

private val logger = KotlinLogging.logger {}

/**
 * Global error handler — every unhandled exception ends up here.
 *
 * Why centralized error handling?
 * Without this, Ktor returns a raw 500 with a stack trace exposed to clients.
 * This plugin intercepts every exception, logs it server-side, and returns a
 * clean, consistent JSON error response that never leaks internal details.
 */
fun Application.configureStatusPages() {
    install(StatusPages) {

        // Handles our own domain exceptions with a proper HTTP status
        exception<AppException> { call, cause ->
            logger.warn { "AppException: ${cause.message}" }
            call.respond(cause.statusCode, ErrorResponse(cause.message ?: "An error occurred"))
        }

        // Handles validation failures (e.g. missing required fields)
        exception<io.ktor.server.plugins.requestvalidation.RequestValidationException> { call, cause ->
            logger.warn { "ValidationException: ${cause.reasons}" }
            call.respond(HttpStatusCode.UnprocessableEntity, ErrorResponse(cause.reasons.joinToString(", ")))
        }

        // Catch-all — never expose internal details to clients
        exception<Throwable> { call, cause ->
            logger.error(cause) { "Unhandled exception on ${call.request.local.method.value} ${call.request.local.uri}" }
            call.respond(HttpStatusCode.InternalServerError, ErrorResponse("An internal error occurred"))
        }

        // Clean 404 response
        status(HttpStatusCode.NotFound) { call, _ ->
            call.respond(HttpStatusCode.NotFound, ErrorResponse("The requested resource was not found"))
        }

        // Clean 401 response
        status(HttpStatusCode.Unauthorized) { call, _ ->
            call.respond(HttpStatusCode.Unauthorized, ErrorResponse("Authentication required"))
        }
    }
}

// ── Domain Exceptions ─────────────────────────────────────────────────────────

/**
 * Base class for all application-level exceptions.
 * Every feature throws a subclass of this — never raw RuntimeException.
 */
sealed class AppException(
    message: String,
    val statusCode: HttpStatusCode
) : Exception(message)

class NotFoundException(message: String) : AppException(message, HttpStatusCode.NotFound)
class UnauthorizedException(message: String) : AppException(message, HttpStatusCode.Unauthorized)
class ForbiddenException(message: String) : AppException(message, HttpStatusCode.Forbidden)
class BadRequestException(message: String) : AppException(message, HttpStatusCode.BadRequest)
class ConflictException(message: String) : AppException(message, HttpStatusCode.Conflict)

// ── Error Response DTO ────────────────────────────────────────────────────────

@Serializable
data class ErrorResponse(val message: String)
