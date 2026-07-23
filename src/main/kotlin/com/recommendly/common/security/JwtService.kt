package com.recommendly.common.security

import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import com.recommendly.common.config.JwtConfig
import java.util.Date
import java.util.UUID

/**
 * Handles all JWT operations.
 *
 * Token strategy:
 * - Access token  : Short-lived (1 hour). Sent in Authorization header on every request.
 * - Refresh token : Long-lived (30 days). Stored in DB (hashed). Used only to get new access tokens.
 *
 * Why two tokens?
 * If the access token is stolen, it expires in 1 hour with no way to extend it.
 * Logout invalidates the refresh token in DB, immediately blocking new access token generation.
 *
 * Claims in access token:
 * - sub  : user UUID (standard "subject" claim)
 * - email: user email (convenience for downstream route handlers)
 * - iss  : issuer (from config)
 * - aud  : audience (from config)
 * - exp  : expiry timestamp
 */
class JwtService(private val config: JwtConfig) {

    private val algorithm = Algorithm.HMAC256(config.secret)
    private val verifier = JWT.require(algorithm)
        .withIssuer(config.issuer)
        .withAudience(config.audience)
        .build()

    /** Generates a signed JWT access token valid for 1 hour. */
    fun generateAccessToken(userId: UUID, email: String): String {
        val now = System.currentTimeMillis()
        return JWT.create()
            .withIssuer(config.issuer)
            .withAudience(config.audience)
            .withSubject(userId.toString())
            .withClaim("email", email)
            .withIssuedAt(Date(now))
            .withExpiresAt(Date(now + ACCESS_TOKEN_TTL_MS))
            .sign(algorithm)
    }

    /**
     * Generates a cryptographically secure refresh token (random UUID).
     * This raw value is returned to the client and stored as a SHA-256 hash in the DB.
     */
    fun generateRefreshToken(): String = UUID.randomUUID().toString()

    /**
     * Verifies and decodes a JWT. Returns null if the token is expired, tampered, or invalid.
     * Never throws — callers should treat null as "unauthorized".
     */
    fun verifyAccessToken(token: String): TokenClaims? = try {
        val decoded = verifier.verify(token)
        TokenClaims(
            userId = UUID.fromString(decoded.subject),
            email  = decoded.getClaim("email").asString()
        )
    } catch (e: Exception) {
        null
    }

    companion object {
        const val ACCESS_TOKEN_TTL_MS  = 60 * 60 * 1_000L          // 1 hour
        const val REFRESH_TOKEN_TTL_MS = 30L * 24 * 60 * 60 * 1_000 // 30 days
    }
}

data class TokenClaims(
    val userId: UUID,
    val email:  String
)
