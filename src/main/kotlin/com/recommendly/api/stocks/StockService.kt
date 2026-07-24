package com.recommendly.api.stocks

import com.recommendly.common.cache.RedisFactory
import com.recommendly.common.stockdata.LiveQuote
import com.recommendly.common.stockdata.YahooFinanceService
import com.recommendly.plugins.NotFoundException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import mu.KotlinLogging

private val logger = KotlinLogging.logger {}

/**
 * Business logic for the Stocks feature.
 *
 * Data flow:
 * 1. Check Redis cache (fast, in-memory)
 * 2. Cache miss → call Yahoo Finance API (external HTTP)
 * 3. Write result to Redis with TTL
 * 4. Return data
 *
 * Cache TTLs:
 * - All quotes:  60 seconds  (refresh roughly every minute)
 * - Candles 1d:  5 minutes   (intraday — bars update every 5min anyway)
 * - Candles other: 15 minutes (daily/weekly data changes infrequently)
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
        /**
         * The symbols this app tracks. In a future phase this will be stored
         * in the DB so admins can add/remove symbols without a code deploy.
         */
        val TRACKED_SYMBOLS = listOf(
            "AAPL", "MSFT", "GOOGL", "META",  "NVDA",
            "AMZN", "TSLA", "INTC",  "AMD",   "ORCL",
            "JPM",  "BAC",  "GS",    "V",     "MA",
            "JNJ",  "UNH",  "PFE",   "ABBV",  "MRK",
            "WMT",  "HD",   "NKE",   "SBUX",
            "XOM",  "CVX",  "COP",
            "T",    "VZ"
        )

        private const val QUOTES_TTL_SEC  = 60L
        private const val CANDLES_1D_TTL  = 300L   // 5 min
        private const val CANDLES_TTL     = 900L   // 15 min
    }

    // ── Public API ─────────────────────────────────────────────────────────────

    /**
     * Returns live quotes for all tracked symbols.
     * All 29 symbols are fetched in a single Yahoo Finance API call.
     */
    suspend fun getAllQuotes(): List<QuoteDto> {
        val key = "stock:quotes:all"
        val cached = redisGet(key)
        if (cached != null) {
            logger.debug { "Cache HIT: $key" }
            return json.decodeFromString(cached)
        }

        logger.debug { "Cache MISS: $key — fetching from Yahoo Finance" }
        val quotes = yahoo.fetchQuotes(TRACKED_SYMBOLS).map { it.toDto() }
        redisSetEx(key, QUOTES_TTL_SEC, json.encodeToString(quotes))
        return quotes
    }

    /**
     * Returns the live quote for a single symbol.
     * Uses the all-quotes cache if warm, otherwise fetches individually.
     */
    suspend fun getQuote(symbol: String): QuoteDto {
        val upper = symbol.trim().uppercase()
        val key   = "stock:quote:$upper"
        val cached = redisGet(key)
        if (cached != null) {
            logger.debug { "Cache HIT: $key" }
            return json.decodeFromString(cached)
        }

        logger.debug { "Cache MISS: $key — fetching from Yahoo Finance" }
        val quote = yahoo.fetchQuotes(listOf(upper)).firstOrNull()?.toDto()
            ?: throw NotFoundException("Stock '$upper' not found or not available")

        redisSetEx(key, QUOTES_TTL_SEC, json.encodeToString(quote))
        return quote
    }

    /**
     * Returns OHLCV candlestick data for one symbol + time period.
     *
     * @param period "1d" | "5d" | "1mo" | "3mo" | "6mo" | "1y" | "5y"
     */
    suspend fun getCandles(symbol: String, period: String): CandlesResponse {
        val upper     = symbol.trim().uppercase()
        val safePeriod = period.trim().ifBlank { "3mo" }
        val key       = "stock:candles:$upper:$safePeriod"
        val ttl       = if (safePeriod == "1d") CANDLES_1D_TTL else CANDLES_TTL

        val cached = redisGet(key)
        if (cached != null) {
            logger.debug { "Cache HIT: $key" }
            return json.decodeFromString(cached)
        }

        logger.debug { "Cache MISS: $key — fetching from Yahoo Finance" }
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
