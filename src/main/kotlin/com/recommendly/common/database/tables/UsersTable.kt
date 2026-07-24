package com.recommendly.common.database.tables

import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.kotlin.datetime.timestampWithTimeZone

/**
 * Mirrors the SQL schema in V1__create_users_table.sql exactly.
 *
 * Why a Table object instead of a DAO Entity?
 * The DSL-style (Table + selectAll/insert/update) gives us explicit SQL control,
 * which is important in a financial app where we never want magic behind the scenes.
 *
 * Note: created_at / updated_at have no Kotlin-side default because they use
 * PostgreSQL's DEFAULT NOW() from the migration. The DB fills them automatically.
 */
object UsersTable : Table("users") {
    val id           = uuid("id").autoGenerate()
    val email        = varchar("email", 255).uniqueIndex()
    val displayName  = varchar("display_name", 100)
    val passwordHash = varchar("password_hash", 255).nullable()
    val isActive     = bool("is_active").default(true)
    val isPremium    = bool("is_premium").default(false)
    val createdAt    = timestampWithTimeZone("created_at")
    val updatedAt    = timestampWithTimeZone("updated_at")

    override val primaryKey = PrimaryKey(id)
}
