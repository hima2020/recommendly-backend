package com.recommendly.plugins

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.plugins.calllogging.*
import io.ktor.server.plugins.cors.routing.*
import io.ktor.server.plugins.defaultheaders.*
import io.ktor.server.request.*
import org.slf4j.event.Level

/**
 * Configures all HTTP-level concerns:
 * - CORS: controls which origins can call our API
 * - Default headers: security headers on every response
 * - Call logging: structured request logs
 */
fun Application.configureHTTP() {

    // ── CORS ─────────────────────────────────────────────────────────────────
    // In Phase 1 we allow any host (Android app + future web dashboard).
    // When we launch, we will tighten this to specific origins.
    install(CORS) {
        allowMethod(HttpMethod.Options)
        allowMethod(HttpMethod.Get)
        allowMethod(HttpMethod.Post)
        allowMethod(HttpMethod.Put)
        allowMethod(HttpMethod.Delete)
        allowMethod(HttpMethod.Patch)
        allowHeader(HttpHeaders.Authorization)
        allowHeader(HttpHeaders.ContentType)
        allowHeader("X-Request-ID")
        anyHost() // TODO: restrict to specific origins before public launch
    }

    // ── Security Headers ─────────────────────────────────────────────────────
    install(DefaultHeaders) {
        header("X-Content-Type-Options", "nosniff")
        header("X-Frame-Options", "DENY")
        header("X-XSS-Protection", "1; mode=block")
        header("Referrer-Policy", "strict-origin-when-cross-origin")
        header("Server", "Recommendly") // hide Ktor/Netty version
    }

    // ── Request Logging ──────────────────────────────────────────────────────
    install(CallLogging) {
        level = Level.INFO
        filter { call -> call.request.path().startsWith("/api") }
        format { call ->
            val status = call.response.status()
            val method = call.request.httpMethod.value
            val path = call.request.path()
            val duration = call.processingTimeMillis()
            "$method $path → $status (${duration}ms)"
        }
    }
}
