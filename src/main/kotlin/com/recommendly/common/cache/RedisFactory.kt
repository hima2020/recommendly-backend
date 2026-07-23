package com.recommendly.common.cache

import com.recommendly.common.config.AppConfig
import io.lettuce.core.RedisClient
import io.lettuce.core.RedisURI
import io.lettuce.core.api.StatefulRedisConnection
import io.lettuce.core.api.sync.RedisCommands
import mu.KotlinLogging

private val logger = KotlinLogging.logger {}

/**
 * Manages the Redis connection via Lettuce.
 *
 * Why Redis?
 * - Cache expensive stock analysis results (avoid re-fetching from external APIs)
 * - Cache news, trending stocks, top gainers/losers
 * - Store refresh tokens (fast lookup, easy invalidation)
 * - Rate limiting counters
 *
 * Why Lettuce over Jedis?
 * - Lettuce is async/reactive by default (fits Ktor's coroutine model)
 * - Thread-safe single connection (no pool needed for most use cases)
 * - Better performance under high concurrency
 */
class RedisFactory private constructor(
    private val client: RedisClient,
    private val connection: StatefulRedisConnection<String, String>
) {
    val commands: RedisCommands<String, String> = connection.sync()

    fun close() {
        logger.info { "Closing Redis connection..." }
        connection.close()
        client.shutdown()
    }

    companion object {
        fun create(config: AppConfig): RedisFactory {
            logger.info { "Connecting to Redis at ${config.redis.host}:${config.redis.port}..." }

            val uri = RedisURI.builder()
                .withHost(config.redis.host)
                .withPort(config.redis.port)
                .build()

            val client = RedisClient.create(uri)
            val connection = client.connect()

            // Verify connection
            val pong = connection.sync().ping()
            logger.info { "Redis connected — PING: $pong ✓" }

            return RedisFactory(client, connection)
        }
    }
}

/**
 * Extension helpers for common cache patterns.
 * Use these in service classes rather than calling commands directly.
 */
fun RedisFactory.getOrSet(key: String, ttlSeconds: Long, compute: () -> String): String {
    val cached = commands.get(key)
    if (cached != null) return cached

    val value = compute()
    commands.setex(key, ttlSeconds, value)
    return value
}

fun RedisFactory.invalidate(key: String) {
    commands.del(key)
}

fun RedisFactory.invalidatePattern(pattern: String) {
    val keys = commands.keys(pattern)
    if (keys.isNotEmpty()) commands.del(*keys.toTypedArray())
}
