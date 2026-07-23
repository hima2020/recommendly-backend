package com.recommendly.common.config

/**
 * Centralised configuration loaded from environment variables.
 *
 * Why environment variables instead of hardcoded values?
 * - Secrets never enter the codebase or git history
 * - The same Docker image runs in dev, staging, and production
 * - Values can change per deployment without rebuilding
 *
 * All required variables have no default — the app fails fast at startup
 * if they are missing, rather than failing mysteriously at runtime.
 */
data class AppConfig(
    val database: DatabaseConfig,
    val redis: RedisConfig,
    val jwt: JwtConfig
) {
    companion object {
        fun load(): AppConfig = AppConfig(
            database = DatabaseConfig(
                url = requireEnv("DATABASE_URL"),
                user = requireEnv("DATABASE_USER"),
                password = requireEnv("DATABASE_PASSWORD"),
                poolSize = System.getenv("DATABASE_POOL_SIZE")?.toInt() ?: 10
            ),
            redis = RedisConfig(
                host = System.getenv("REDIS_HOST") ?: "localhost",
                port = System.getenv("REDIS_PORT")?.toInt() ?: 6379
            ),
            jwt = JwtConfig(
                secret = requireEnv("JWT_SECRET"),
                issuer = System.getenv("JWT_ISSUER") ?: "recommendly",
                audience = System.getenv("JWT_AUDIENCE") ?: "recommendly-users",
                expirationMs = System.getenv("JWT_EXPIRATION_MS")?.toLong() ?: 86_400_000L // 24h
            )
        )

        private fun requireEnv(name: String): String =
            System.getenv(name) ?: error("Required environment variable '$name' is not set")
    }
}

data class DatabaseConfig(
    val url: String,
    val user: String,
    val password: String,
    val poolSize: Int
)

data class RedisConfig(
    val host: String,
    val port: Int
)

data class JwtConfig(
    val secret: String,
    val issuer: String,
    val audience: String,
    val expirationMs: Long
)
