package com.recommendly.api.users

import io.ktor.http.*
import io.ktor.server.auth.*
import io.ktor.server.auth.jwt.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import java.util.UUID

/**
 * User profile routes — all require a valid JWT ("jwt" auth scheme).
 *
 * GET  /api/v1/users/me  — return the authenticated user's profile
 * PATCH /api/v1/users/me — update the authenticated user's display name
 *
 * How auth works:
 * `authenticate("jwt")` wraps the routes. Ktor verifies the Bearer token
 * from the Authorization header before the handler runs. If the token is
 * missing or invalid, Ktor returns 401 automatically (handled by StatusPages).
 *
 * Inside a handler, `call.principal<JWTPrincipal>()!!.subject` gives us
 * the userId UUID string that was baked into the token at login.
 */
fun Route.userRoutes(userService: UserService) {

    authenticate("jwt") {

        route("/users") {

            // ── GET /api/v1/users/me ──────────────────────────────────────────
            // Returns the current user's profile. Fast — just one DB read.
            get("/me") {
                val principal = call.principal<JWTPrincipal>()!!
                val userId    = UUID.fromString(principal.subject!!)
                val profile   = userService.getProfile(userId)
                call.respond(HttpStatusCode.OK, profile)
            }

            // ── PATCH /api/v1/users/me ────────────────────────────────────────
            // Updates mutable profile fields. Returns the full updated profile.
            patch("/me") {
                val principal = call.principal<JWTPrincipal>()!!
                val userId    = UUID.fromString(principal.subject!!)
                val request   = call.receive<UpdateProfileRequest>()
                val updated   = userService.updateProfile(userId, request)
                call.respond(HttpStatusCode.OK, updated)
            }
        }
    }
}
