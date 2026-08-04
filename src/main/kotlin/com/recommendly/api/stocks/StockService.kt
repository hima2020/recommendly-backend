package com.recommendly.api.stocks

import com.recommendly.common.cache.RedisFactory
import com.recommendly.common.stockdata.YahooFinanceService
import com.recommendly.common.stockdata.LiveQuote
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import mu.KotlinLogging

private val logger = KotlinLogging.logger {}

class StockService(
    private val yahoo: YahooFinanceService,
    private val redis: RedisFactory
) {
    // No hardcoded symbols — Mubasher returns all EGX stocks dynamically

    // ── Public API ────────────────────────────────────────────────────────────

    /** Returns all EGX stocks. Cached in Redis for 60 seconds. */
    suspend fun getAllQuotes(): List<QuoteDto> {
        val cacheKey = "egx:quotes:all"
        val cached = withContext(Dispatchers.IO) { redis.sync().get(cacheKey) }
        if (cached != null) {
            return try { Json.decodeFromString(cached) } catch (_: Exception) { emptyList() }
        }

        val quotes = yahoo.fetchQuotes().map { it.toDto() }
        if (quotes.isNotEmpty()) {
            val json = Json.encodeToString(quotes)
            withContext(Dispatchers.IO) { redis.sync().setex(cacheKey, 60L, json) }
        }
        return quotes
    }

    /**
     * Returns a single stock quote by EGX code, e.g. "SWDY".
     * Fetches from the cached all-quotes list — no extra API call.
     */
    suspend fun getQuote(symbol: String): QuoteDto {
        val all = getAllQuotes()
        return all.find { it.symbol.equals(symbol, ignoreCase = true) }
            ?: throw com.recommendly.common.exceptions.AppException(
                com.recommendly.common.exceptions.ErrorCode.NOT_FOUND,
                "Stock '$symbol' not found on EGX"
            )
    }

    /** Returns OHLCV candles for a symbol and period. Cached 5–15 minutes. */
    suspend fun getCandles(symbol: String, period: String): CandlesResponse {
        val cacheKey = "egx:candles:$symbol:$period"
        val ttlSecs  = if (period == "1d" || period == "5d") 300L else 900L

        val cached = withContext(Dispatchers.IO) { redis.sync().get(cacheKey) }
        if (cached != null) {
            return try { Json.decodeFromString(cached) } catch (_: Exception) {
                CandlesResponse(symbol, period, emptyList())
            }
        }

        val candles  = yahoo.fetchCandles(symbol, period).map {
            CandleDto(it.timestamp, it.open, it.high, it.low, it.close, it.volume)
        }
        val response = CandlesResponse(symbol, period, candles)
        val json     = Json.encodeToString(response)
        withContext(Dispatchers.IO) { redis.sync().setex(cacheKey, ttlSecs, json) }
        return response
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
