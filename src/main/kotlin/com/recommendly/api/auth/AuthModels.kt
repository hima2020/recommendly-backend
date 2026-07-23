package com.recommendly.api.auth

import kotlinx.serialization.Serializable
import java.util.UUID

// ── Request bodies ────────────────────────────────────────────────────────────

@Serializable
data class RegisterRequest(
    val email: String,
    val password: String,
    val displayName: String
)

@Serializable
data class LoginRequest(
    val email: String,
    val password: String
)

@Serializable
data class RefreshRequest(
    val refreshToken: String
)

// ── Response bodies ───────────────────────────────────────────────────────────

@Serializable
data class AuthResponse(
    val accessToken: String,
    val refreshToken: String,
    val user: UserDto
)

@Serializable
data class UserDto(
    val id: String,          // UUID as string (safe for JSON / Android Gson)
    val email: String,
    val displayName: String,
    val isPremium: Boolean
)

// ── Internal domain model (never serialized directly) ─────────────────────────

data class UserRecord(
    val id: UUID,
    val email: String,
    val displayName: String,
    val passwordHash: String?,
    val isActive: Boolean,
    val isPremium: Boolean
) {
    fun toDto() = UserDto(
        id          = id.toString(),
        email       = email,
        displayName = displayName,
        isPremium   = isPremium
    )
}
