package com.recommendly.common.stockdata

import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.serialization.json.*
import mu.KotlinLogging
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

private val logger = KotlinLogging.logger {}

/**
 * Fetches real-time stock quotes and OHLCV candle data from Financial Modeling Prep (FMP).
 *
 * Why FMP over Yahoo Finance?
 * - Yahoo Finance blocks datacenter / cloud-provider IPs (Hetzner, AWS, etc.)
 * - FMP works from any IP including VPS/cloud servers
 * - Free tier: 250 API calls/day — plenty for our 29-symbol batch (1 call per refresh)
 * - Returns clean JSON with consistent field names
 *
 * API key is read from the FMP_API_KEY environment variable.
 * Get a free key (no credit card) at https://financialmodelingprep.com/developer/docs/
 */
class YahooFinanceService {

    private val apiKey  = System.getenv("FMP_API_KEY") ?: ""
    private val baseUrl = "https://financialmodelingprep.com/api/v3"

    private val client = HttpClient(CIO) {
        install(ContentNegotiation) {
            json(Json {
                ignoreUnknownKeys = true
                isLenient         = true
                coerceInputValues = true
            })
        }
        install(HttpTimeout) {
            requestTimeoutMillis = 10_000
            connectTimeoutMillis = 5_000
            socketTimeoutMillis  = 10_000
        }
    }

    // ── Public API ────────────────────────────────────────────────────────────

    /**
     * Fetches real-time quotes for up to 50 symbols in a single HTTP call.
     * FMP batch endpoint: GET /api/v3/quote/AAPL,MSFT,...?apikey=KEY
     * Returns an empty list on any error — callers handle gracefully.
     */
    suspend fun fetchQuotes(symbols: List<String>): List<LiveQuote> {
        if (symbols.isEmpty()) return emptyList()
        val joined = symbols.joinToString(",")
        val url    = "$baseUrl/quote/$joined?apikey=$apiKey"

        return try {
            val element: JsonElement = client.get(url).body()
            val array = element as? JsonArray ?: return emptyList()
            array.mapNotNull { it.jsonObject.toLiveQuoteOrNull() }
        } catch (e: Exception) {
            logger.error(e) { "fetchQuotes failed for: $joined" }
            emptyList()
        }
    }

    /**
     * Fetches OHLCV candlestick data for one symbol.
     *
     * @param symbol  Ticker symbol, e.g. "AAPL"
     * @param period  One of: "1d" "5d" "1mo" "3mo" "6mo" "1y" "5y"
     *
     * Intraday periods (1d, 5d) → FMP historical-chart/{interval}/{symbol}
     * Multi-day periods         → FMP historical-price-full/{symbol}?timeseries=N
     */
    suspend fun fetchCandles(symbol: String, period: String): List<Candle> {
        return try {
            when (period) {
                "1d"  -> fetchIntradayCandles(symbol, "5min")
                "5d"  -> fetchIntradayCandles(symbol, "30min")
                else  -> fetchDailyCandles(symbol, periodToDays(period))
            }
        } catch (e: Exception) {
            logger.error(e) { "fetchCandles failed: $symbol ($period)" }
            emptyList()
        }
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    /**
     * Intraday candles from FMP.
     * Response is a JSON array sorted newest-first; we reverse to get chronological order.
     * GET /api/v3/historical-chart/{interval}/{symbol}?apikey=KEY
     */
    private suspend fun fetchIntradayCandles(symbol: String, interval: String): List<Candle> {
        val url    = "$baseUrl/historical-chart/$interval/$symbol?apikey=$apiKey"
        val element: JsonElement = client.get(url).body()
        val array = element as? JsonArray ?: return emptyList()
        return array.mapNotNull { it.jsonObject.toIntradayCandleOrNull() }.reversed()
    }

    /**
     * Daily candles from FMP using the timeseries shortcut (last N trading days).
     * Response is a JSON object with "historical" array sorted newest-first.
     * GET /api/v3/historical-price-full/{symbol}?timeseries=N&apikey=KEY
     */
    private suspend fun fetchDailyCandles(symbol: String, timeseries: Int): List<Candle> {
        val url    = "$baseUrl/historical-price-full/$symbol?timeseries=$timeseries&apikey=$apiKey"
        val element: JsonElement = client.get(url).body()
        val obj = element as? JsonObject ?: return emptyList()
        return obj["historical"]
            ?.jsonArray
            ?.mapNotNull { it.jsonObject.toDailyCandleOrNull() }
            ?.reversed()
            ?: emptyList()
    }

    /** Maps period codes to approximate number of trading days to fetch. */
    private fun periodToDays(period: String): Int = when (period) {
        "1mo" -> 30
        "3mo" -> 90
        "6mo" -> 180
        "1y"  -> 365
        "5y"  -> 1825
        else  -> 90
    }

    // ── JSON mappers ──────────────────────────────────────────────────────────

    private fun JsonObject.toLiveQuoteOrNull(): LiveQuote? {
        val symbol = this["symbol"]?.jsonPrimitive?.contentOrNull ?: return null
        val price  = this["price"]?.jsonPrimitive?.doubleOrNull   ?: return null
        return LiveQuote(
            symbol        = symbol,
            name          = this["name"]?.jsonPrimitive?.contentOrNull ?: symbol,
            exchange      = this["exchange"]?.jsonPrimitive?.contentOrNull ?: "",
            price         = price,
            change        = this["change"]?.jsonPrimitive?.doubleOrNull ?: 0.0,
            changePercent = this["changesPercentage"]?.jsonPrimitive?.doubleOrNull ?: 0.0,
            open          = this["open"]?.jsonPrimitive?.doubleOrNull,
            high          = this["dayHigh"]?.jsonPrimitive?.doubleOrNull,
            low           = this["dayLow"]?.jsonPrimitive?.doubleOrNull,
            prevClose     = this["previousClose"]?.jsonPrimitive?.doubleOrNull,
            volume        = this["volume"]?.jsonPrimitive?.longOrNull ?: 0L,
            marketCap     = this["marketCap"]?.jsonPrimitive?.longOrNull
        )
    }

    /** FMP intraday date format: "2024-01-10 09:35:00" */
    private val intradayFmt = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")

    private fun JsonObject.toIntradayCandleOrNull(): Candle? {
        val dateStr = this["date"]?.jsonPrimitive?.contentOrNull ?: return null
        val open    = this["open"]?.jsonPrimitive?.doubleOrNull  ?: return null
        val high    = this["high"]?.jsonPrimitive?.doubleOrNull  ?: return null
        val low     = this["low"]?.jsonPrimitive?.doubleOrNull   ?: return null
        val close   = this["close"]?.jsonPrimitive?.doubleOrNull ?: return null
        val volume  = this["volume"]?.jsonPrimitive?.longOrNull  ?: 0L
        val ts = runCatching {
            LocalDateTime.parse(dateStr, intradayFmt).toEpochSecond(ZoneOffset.UTC)
        }.getOrNull() ?: return null
        return Candle(ts, open, high, low, close, volume)
    }

    /** FMP daily date format: "2024-01-10" */
    private val dailyFmt = DateTimeFormatter.ofPattern("yyyy-MM-dd")

    private fun JsonObject.toDailyCandleOrNull(): Candle? {
        val dateStr = this["date"]?.jsonPrimitive?.contentOrNull ?: return null
        val open    = this["open"]?.jsonPrimitive?.doubleOrNull  ?: return null
        val high    = this["high"]?.jsonPrimitive?.doubleOrNull  ?: return null
        val low     = this["low"]?.jsonPrimitive?.doubleOrNull   ?: return null
        val close   = this["close"]?.jsonPrimitive?.doubleOrNull ?: return null
        val volume  = this["volume"]?.jsonPrimitive?.longOrNull  ?: 0L
        val ts = runCatching {
            LocalDate.parse(dateStr, dailyFmt).atStartOfDay().toEpochSecond(ZoneOffset.UTC)
        }.getOrNull() ?: return null
        return Candle(ts, open, high, low, close, volume)
    }
}

// ── Domain models ─────────────────────────────────────────────────────────────

data class LiveQuote(
    val symbol:        String,
    val name:          String,
    val exchange:      String,
    val price:         Double,
    val change:        Double,
    val changePercent: Double,
    val open:          Double?,
    val high:          Double?,
    val low:           Double?,
    val prevClose:     Double?,
    val volume:        Long,
    val marketCap:     Long?
)

data class Candle(
    val timestamp: Long,   // Unix epoch in SECONDS
    val open:      Double,
    val high:      Double,
    val low:       Double,
    val close:     Double,
    val volume:    Long
)
