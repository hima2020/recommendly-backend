package com.recommendly.common.database.tables

import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.kotlin.datetime.CurrentTimestamp
import org.jetbrains.exposed.sql.kotlin.datetime.timestampWithTimeZone

/**
 * Mirrors the SQL schema in V1__create_users_table.sql exactly.
 *
 * Why a Table object instead of a DAO Entity?
 * The DSL-style (Table + select/insert/update) gives us explicit SQL control,
 * which is important in a financial app where we never want magic behind the scenes.
 */
object UsersTable : Table("users") {
    val id            = uuid("id").autoGenerate()
    val email         = varchar("email", 255).uniqueIndex()
    val displayName   = varchar("display_name", 100)
    val passwordHash  = varchar("password_hash", 255).nullable()
    val isActive      = bool("is_active").default(true)
    val isPremium     = bool("is_premium").default(false)
    val createdAt     = timestampWithTimeZone("created_at").defaultExpression(CurrentTimestamp)
    val updatedAt     = timestampWithTimeZone("updated_at").defaultExpression(CurrentTimestamp)

    override val primaryKey = PrimaryKey(id)
}
