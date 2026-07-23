package com.recommendly.api.auth

import com.recommendly.plugins.BadRequestException
import com.recommendly.plugins.ConflictException
import com.recommendly.plugins.ForbiddenException
import com.recommendly.plugins.UnauthorizedException
import com.recommendly.common.security.HashUtils
import com.recommendly.common.security.JwtService
import com.recommendly.common.security.PasswordService
import mu.KotlinLogging
import java.time.OffsetDateTime
import java.util.UUID

private val logger = KotlinLogging.logger {}

/**
 * Business logic for authentication.
 *
 * This class coordinates between:
 * - AuthRepository (DB reads/writes)
 * - PasswordService (BCrypt)
 * - JwtService (token generation)
 *
 * It never touches HTTP (no call, no respond, no status codes).
 * HTTP concerns live in AuthRoutes.
 */
class AuthService(
    private val repo: AuthRepository,
    private val passwordService: PasswordService,
    private val jwtService: JwtService
) {

    /**
     * Creates a new account.
     *
     * Steps:
     * 1. Validate inputs
     * 2. Confirm email is not already registered
     * 3. BCrypt-hash the password
     * 4. Insert user row
     * 5. Issue access + refresh tokens
     */
    suspend fun register(request: RegisterRequest): AuthResponse {
        validateRegisterRequest(request)

        if (repo.emailExists(request.email)) {
            throw ConflictException("An account with this email already exists")
        }

        val hash = passwordService.hash(request.password)
        val user = repo.createUser(
            email        = request.email,
            displayName  = request.displayName.trim(),
            passwordHash = hash
        )

        logger.info { "New user registered: ${user.id}" }
        return issueTokens(user)
    }

    /**
     * Authenticates an existing user.
     *
     * Steps:
     * 1. Look up user by email
     * 2. Verify BCrypt password
     * 3. Check account is active
     * 4. Issue access + refresh tokens
     *
     * Security: always return "invalid credentials" — never reveal whether the
     * email exists or the password is wrong. That prevents email enumeration attacks.
     */
    suspend fun login(request: LoginRequest): AuthResponse {
        if (request.email.isBlank() || request.password.isBlank()) {
            throw BadRequestException("Email and password are required")
        }

        val user = repo.findUserByEmail(request.email)

        // Constant-time path: verify even if user is null, prevents timing attacks
        val passwordMatches = user?.passwordHash?.let {
            passwordService.verify(request.password, it)
        } ?: false

        if (user == null || !passwordMatches) {
            throw UnauthorizedException("Invalid email or password")
        }

        if (!user.isActive) {
            throw ForbiddenException("This account has been deactivated")
        }

        logger.info { "User logged in: ${user.id}" }
        return issueTokens(user)
    }

    /**
     * Issues a new access token using a valid refresh token.
     *
     * The old refresh token is deleted and a new one is issued (token rotation).
     * Rotation means a stolen refresh token becomes invalid after first use.
     */
    suspend fun refresh(request: RefreshRequest): AuthResponse {
        if (request.refreshToken.isBlank()) {
            throw BadRequestException("Refresh token is required")
        }

        val tokenHash = HashUtils.sha256(request.refreshToken)
        val userId    = repo.findValidRefreshToken(tokenHash)
            ?: throw UnauthorizedException("Refresh token is invalid or expired")

        val user = repo.findUserById(userId)
            ?: throw UnauthorizedException("User not found")

        if (!user.isActive) {
            throw ForbiddenException("This account has been deactivated")
        }

        // Rotate: delete old token, issue fresh pair
        repo.deleteRefreshToken(tokenHash)
        logger.info { "Token refreshed for user: ${user.id}" }
        return issueTokens(user)
    }

    /**
     * Revokes the provided refresh token (single device logout).
     * If the token doesn't exist, we succeed silently — logout should always succeed.
     */
    suspend fun logout(refreshToken: String) {
        val tokenHash = HashUtils.sha256(refreshToken)
        repo.deleteRefreshToken(tokenHash)
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private suspend fun issueTokens(user: UserRecord): AuthResponse {
        val accessToken  = jwtService.generateAccessToken(user.id, user.email)
        val refreshToken = jwtService.generateRefreshToken()
        val expiresAt    = OffsetDateTime.now().plusSeconds(
            JwtService.REFRESH_TOKEN_TTL_MS / 1_000
        )

        repo.saveRefreshToken(
            userId    = user.id,
            tokenHash = HashUtils.sha256(refreshToken),
            expiresAt = expiresAt
        )

        return AuthResponse(
            accessToken  = accessToken,
            refreshToken = refreshToken,
            user         = user.toDto()
        )
    }

    private fun validateRegisterRequest(request: RegisterRequest) {
        if (request.email.isBlank()) {
            throw BadRequestException("Email is required")
        }
        if (!request.email.contains("@") || !request.email.contains(".")) {
            throw BadRequestException("Email format is invalid")
        }
        if (request.password.length < 8) {
            throw BadRequestException("Password must be at least 8 characters")
        }
        if (request.displayName.isBlank()) {
            throw BadRequestException("Display name is required")
        }
        if (request.displayName.trim().length < 2) {
            throw BadRequestException("Display name must be at least 2 characters")
        }
    }
}
