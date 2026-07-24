package com.recommendly.api.users

import kotlinx.serialization.Serializable

/**
 * Request body for PATCH /api/v1/users/me.
 *
 * Only displayName is mutable through this endpoint.
 * - Email changes require a verification flow (future Phase).
 * - isPremium is set by the billing system, not directly by users.
 */
@Serializable
data class UpdateProfileRequest(
    val displayName: String
)
