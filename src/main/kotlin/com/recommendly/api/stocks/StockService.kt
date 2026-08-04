package com.recommendly.api.stocks

import com.recommendly.common.cache.RedisFactory
import com.recommendly.common.stockdata.LiveQuote
import com.recommendly.common.stockdata.YahooFinanceService
import com.recommendly.plugins.NotFoundException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import mu.KotlinLogging

private val logger = KotlinLogging.logger {}

/**
 * Business logic for the Stocks feature.
 *
 * Data source: Mubasher (mubasher.info) — EGX (Egyptian Exchange) live data.
 * No API key required. All EGX stocks returned in a single call.
 *
 * Cache TTLs:
 * - All quotes:    60 seconds  (refresh roughly every minute)
 * - Candles 1d:    5 minutes   (intraday — bars update every 5min anyway)
 * - Candles other: 15 minutes  (daily/weekly data changes infrequently)
 */
class StockService(
    private val yahoo: YahooFinanceService,
    private val redis: RedisFactory
) {
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults    = true
    }

    companion object {
        private const val QUOTES_TTL_SEC = 60L
        private const val CANDLES_1D_TTL = 300L   // 5 min
        private const val CANDLES_TTL    = 900L   // 15 min
    }

    // ── Public API ─────────────────────────────────────────────────────────────

    /** Returns live quotes for all EGX stocks from Mubasher. */
    suspend fun getAllQuotes(): List<QuoteDto> {
        val key    = "egx:quotes:all"
        val cached = redisGet(key)
        if (cached != null) {
            logger.debug { "Cache HIT: $key" }
            return json.decodeFromString(cached)
        }

        logger.debug { "Cache MISS: $key — fetching from Mubasher" }
        val quotes = yahoo.fetchQuotes().map { it.toDto() }
        if (quotes.isNotEmpty()) {
            redisSetEx(key, QUOTES_TTL_SEC, json.encodeToString(quotes))
        }
        return quotes
    }

    /**
     * Returns the live quote for a single EGX symbol, e.g. "SWDY".
     * Reuses the all-quotes cache when warm — no extra API call.
     */
    suspend fun getQuote(symbol: String): QuoteDto {
        val upper = symbol.trim().uppercase()
        val all   = getAllQuotes()
        return all.find { it.symbol.equals(upper, ignoreCase = true) }
            ?: throw NotFoundException("Stock '$upper' not found on EGX")
    }

    /**
     * Returns OHLCV candlestick data for one symbol + time period.
     *
     * @param period "1d" | "5d" | "1mo" | "3mo" | "6mo" | "1y" | "5y"
     */
    suspend fun getCandles(symbol: String, period: String): CandlesResponse {
        val upper      = symbol.trim().uppercase()
        val safePeriod = period.trim().ifBlank { "3mo" }
        val key        = "egx:candles:$upper:$safePeriod"
        val ttl        = if (safePeriod == "1d") CANDLES_1D_TTL else CANDLES_TTL

        val cached = redisGet(key)
        if (cached != null) {
            logger.debug { "Cache HIT: $key" }
            return json.decodeFromString(cached)
        }

        logger.debug { "Cache MISS: $key — fetching from Mubasher" }
        val candles = yahoo.fetchCandles(upper, safePeriod).map {
            CandleDto(it.timestamp, it.open, it.high, it.low, it.close, it.volume)
        }

        val response = CandlesResponse(upper, safePeriod, candles)
        redisSetEx(key, ttl, json.encodeToString(response))
        return response
    }

    // ── Redis helpers (IO dispatcher — sync Lettuce commands are blocking) ────

    private suspend fun redisGet(key: String): String? = withContext(Dispatchers.IO) {
        runCatching { redis.commands.get(key) }.getOrNull()
    }

    private suspend fun redisSetEx(key: String, ttl: Long, value: String) =
        withContext(Dispatchers.IO) {
            runCatching { redis.commands.setex(key, ttl, value) }
        }

    // ── Mapper ────────────────────────────────────────────────────────────────

    private fun LiveQuote.toDto() = QuoteDto(
        symbol        = symbol,
        name          = name,
        exchange      = exchange,
        price         = price,
        change        = change,
        changePercent = changePercent,
        open          = open,
        high          = high,
        low           = low,
        prevClose     = prevClose,
        volume        = volume,
        marketCap     = marketCap
    )
}
