package com.recommendly.api.users

import com.recommendly.api.auth.UserRecord
import com.recommendly.common.database.dbQuery
import com.recommendly.common.database.tables.UsersTable
import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.update
import java.time.OffsetDateTime
import java.util.UUID

/**
 * Data access layer for user profile operations.
 *
 * Separate from AuthRepository by design:
 * - AuthRepository owns authentication-specific reads (login, refresh, token checks)
 * - UserRepository owns profile reads and mutations (GET /me, PATCH /me)
 *
 * Both talk to the same UsersTable — that's fine for this scale.
 * If contention ever becomes an issue, we can introduce caching here.
 */
class UserRepository {

    /** Fetches a user's full record by their UUID. Returns null if not found. */
    suspend fun findById(id: UUID): UserRecord? = dbQuery {
        UsersTable
            .selectAll()
            .where { UsersTable.id eq id }
            .singleOrNull()
            ?.toUserRecord()
    }

    /**
     * Updates the user's display name and stamps updatedAt.
     * Returns the full updated record so the caller doesn't need a second query.
     */
    suspend fun updateDisplayName(id: UUID, displayName: String): UserRecord = dbQuery {
        UsersTable.update({ UsersTable.id eq id }) {
            it[UsersTable.displayName] = displayName
            it[UsersTable.updatedAt]   = OffsetDateTime.now()
        }
        // Re-fetch after update — gives us a consistent snapshot
        UsersTable
            .selectAll()
            .where { UsersTable.id eq id }
            .single()
            .toUserRecord()
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
