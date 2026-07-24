package com.recommendly.api.users

import com.recommendly.api.auth.UserDto
import com.recommendly.plugins.BadRequestException
import com.recommendly.plugins.NotFoundException
import mu.KotlinLogging
import java.util.UUID

private val logger = KotlinLogging.logger {}

/**
 * Business logic for user profile operations.
 *
 * This class contains all validation and orchestration — routes stay thin,
 * repository stays dumb. HTTP concerns (call, respond, status codes) live in UserRoutes.
 */
class UserService(
    private val repo: UserRepository
) {

    /**
     * Returns the authenticated user's profile.
     * Throws NotFoundException if the UUID in the JWT doesn't match any DB row
     * (e.g. account was deleted after the token was issued).
     */
    suspend fun getProfile(userId: UUID): UserDto {
        return repo.findById(userId)?.toDto()
            ?: throw NotFoundException("User not found")
    }

    /**
     * Updates the display name for the authenticated user.
     *
     * Validation rules:
     * - Must be at least 2 characters (after trimming whitespace)
     * - Must be at most 100 characters (matches DB column constraint)
     */
    suspend fun updateProfile(userId: UUID, request: UpdateProfileRequest): UserDto {
        val trimmed = request.displayName.trim()

        if (trimmed.length < 2) {
            throw BadRequestException("Display name must be at least 2 characters")
        }
        if (trimmed.length > 100) {
            throw BadRequestException("Display name must be at most 100 characters")
        }

        logger.info { "Updating display name for user: $userId" }
        return repo.updateDisplayName(userId, trimmed).toDto()
    }
}
