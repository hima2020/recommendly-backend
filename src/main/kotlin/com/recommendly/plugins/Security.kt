package com.recommendly.plugins

import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import com.recommendly.common.config.JwtConfig
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.auth.jwt.*

/**
 * Installs JWT authentication so protected routes can use `authenticate("jwt") { }`.
 *
 * What this does:
 * 1. Registers a named JWT validator called "jwt"
 * 2. On every request to a protected route, extracts the Bearer token from Authorization header
 * 3. Verifies the signature, issuer, audience, and expiry
 * 4. Puts the validated principal into the request call so route handlers can read userId/email
 *
 * Routes that need auth:  `authenticate("jwt") { get("/me") { ... } }`
 * Get userId in a route:  `call.principal<JWTPrincipal>()?.subject`
 */
fun Application.configureSecurity(jwtConfig: JwtConfig) {
    val algorithm = Algorithm.HMAC256(jwtConfig.secret)

    install(Authentication) {
        jwt("jwt") {
            realm = "Recommendly API"

            verifier(
                JWT.require(algorithm)
                    .withIssuer(jwtConfig.issuer)
                    .withAudience(jwtConfig.audience)
                    .build()
            )

            validate { credential ->
                // credential.payload contains all JWT claims
                // Return non-null to allow, null to reject
                val subject = credential.payload.subject
                if (!subject.isNullOrBlank()) {
                    JWTPrincipal(credential.payload)
                } else {
                    null
                }
            }

            // challenge {} block is omitted intentionally:
            // StatusPages handles the 401 Unauthorized response globally
        }
    }
}
