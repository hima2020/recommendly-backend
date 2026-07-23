package com.recommendly.api.auth

import io.ktor.http.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*

/**
 * Authentication routes.
 *
 * All dependencies are injected at Application level (Routing.kt) and passed
 * in as parameters — this is required to avoid Koin/Ktor 3.x Route injection issues.
 *
 * POST /api/v1/auth/register  — create account, receive token pair
 * POST /api/v1/auth/login     — authenticate, receive token pair
 * POST /api/v1/auth/refresh   — exchange refresh token for new access token
 * POST /api/v1/auth/logout    — revoke refresh token
 */
fun Route.authRoutes(authService: AuthService) {

    route("/auth") {

        // Register a new account
        post("/register") {
            val request = call.receive<RegisterRequest>()
            val response = authService.register(request)
            call.respond(HttpStatusCode.Created, response)
        }

        // Login with email + password
        post("/login") {
            val request = call.receive<LoginRequest>()
            val response = authService.login(request)
            call.respond(HttpStatusCode.OK, response)
        }

        // Refresh access token using a valid refresh token
        post("/refresh") {
            val request = call.receive<RefreshRequest>()
            val response = authService.refresh(request)
            call.respond(HttpStatusCode.OK, response)
        }

        // Logout — revoke the current refresh token
        post("/logout") {
            val request = call.receive<RefreshRequest>()
            authService.logout(request.refreshToken)
            call.respond(HttpStatusCode.NoContent)
        }
    }
}
