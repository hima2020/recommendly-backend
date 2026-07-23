package com.recommendly.plugins

import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.application.*
import io.ktor.server.plugins.contentnegotiation.*
import kotlinx.serialization.json.Json

/**
 * Configures JSON serialization for all request/response bodies.
 *
 * Settings explained:
 * - ignoreUnknownKeys: safe for API evolution (clients can send extra fields)
 * - prettyPrint: disabled in production (saves bandwidth)
 * - isLenient: accepts unquoted strings (useful for testing)
 * - encodeDefaults: include fields even if they hold default values (explicit contracts)
 */
fun Application.configureSerialization() {
    install(ContentNegotiation) {
        json(Json {
            ignoreUnknownKeys = true
            prettyPrint = false
            isLenient = false
            encodeDefaults = true
        })
    }
}
