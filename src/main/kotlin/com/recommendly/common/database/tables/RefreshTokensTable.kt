package com.recommendly.common.database.tables

import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.kotlin.datetime.timestampWithTimeZone

/**
 * Stores refresh tokens so we can:
 *   1. Validate them on /auth/refresh
 *   2. Revoke them on /auth/logout (delete by token hash)
 *   3. Evict all sessions for a user (delete by userId)
 *
 * We store a SHA-256 hash of the token, never the raw value.
 * If the DB leaks, attackers get hashes they can't easily reverse.
 *
 * Note: created_at uses PostgreSQL's DEFAULT NOW() from V2 migration.
 */
object RefreshTokensTable : Table("refresh_tokens") {
    val id        = uuid("id").autoGenerate()
    val userId    = uuid("user_id").references(UsersTable.id)
    val tokenHash = varchar("token_hash", 64).uniqueIndex()   // SHA-256 hex
    val expiresAt = timestampWithTimeZone("expires_at")
    val createdAt = timestampWithTimeZone("created_at")

    override val primaryKey = PrimaryKey(id)
}
