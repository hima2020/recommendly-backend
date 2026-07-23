package com.recommendly.api.auth

import com.recommendly.common.database.dbQuery
import com.recommendly.common.database.tables.RefreshTokensTable
import com.recommendly.common.database.tables.UsersTable
import mu.KotlinLogging
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import java.time.OffsetDateTime
import java.util.UUID

private val logger = KotlinLogging.logger {}

/**
 * Data access layer for authentication.
 *
 * All functions are suspend functions that delegate blocking JDBC calls
 * to the IO dispatcher via dbQuery {}.
 *
 * This class knows nothing about HTTP, JWT, or BCrypt — it only reads/writes the DB.
 */
class AuthRepository {

    // ── Users ─────────────────────────────────────────────────────────────────

    suspend fun findUserByEmail(email: String): UserRecord? = dbQuery {
        UsersTable
            .select { UsersTable.email eq email.lowercase() }
            .singleOrNull()
            ?.toUserRecord()
    }

    suspend fun findUserById(id: UUID): UserRecord? = dbQuery {
        UsersTable
            .select { UsersTable.id eq id }
            .singleOrNull()
            ?.toUserRecord()
    }

    suspend fun emailExists(email: String): Boolean = dbQuery {
        UsersTable
            .select { UsersTable.email eq email.lowercase() }
            .count() > 0
    }

    suspend fun createUser(
        email: String,
        displayName: String,
        passwordHash: String
    ): UserRecord = dbQuery {
        val id = UsersTable.insert {
            it[UsersTable.email]        = email.lowercase()
            it[UsersTable.displayName]  = displayName
            it[UsersTable.passwordHash] = passwordHash
            it[UsersTable.isActive]     = true
            it[UsersTable.isPremium]    = false
        } get UsersTable.id

        UsersTable
            .select { UsersTable.id eq id }
            .single()
            .toUserRecord()
    }

    // ── Refresh tokens ────────────────────────────────────────────────────────

    suspend fun saveRefreshToken(
        userId: UUID,
        tokenHash: String,
        expiresAt: OffsetDateTime
    ) = dbQuery {
        RefreshTokensTable.insert {
            it[RefreshTokensTable.userId]    = userId
            it[RefreshTokensTable.tokenHash] = tokenHash
            it[RefreshTokensTable.expiresAt] = expiresAt
        }
    }

    /** Returns the userId if the token hash exists and hasn't expired. */
    suspend fun findValidRefreshToken(tokenHash: String): UUID? = dbQuery {
        val now = OffsetDateTime.now()
        RefreshTokensTable
            .select {
                (RefreshTokensTable.tokenHash eq tokenHash) and
                (RefreshTokensTable.expiresAt greater now)
            }
            .singleOrNull()
            ?.get(RefreshTokensTable.userId)
    }

    /** Deletes a specific refresh token (logout from one device). */
    suspend fun deleteRefreshToken(tokenHash: String) = dbQuery {
        RefreshTokensTable.deleteWhere { RefreshTokensTable.tokenHash eq tokenHash }
    }

    /** Deletes ALL refresh tokens for a user (logout from all devices). */
    suspend fun deleteAllRefreshTokens(userId: UUID) = dbQuery {
        RefreshTokensTable.deleteWhere { RefreshTokensTable.userId eq userId }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private fun ResultRow.toUserRecord() = UserRecord(
        id           = this[UsersTable.id],
        email        = this[UsersTable.email],
        displayName  = this[UsersTable.displayName],
        passwordHash = this[UsersTable.passwordHash],
        isActive     = this[UsersTable.isActive],
        isPremium    = this[UsersTable.isPremium]
    )
}
