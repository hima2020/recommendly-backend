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

private val logger = KotlinLogging.logger {}

/**
 * Fetches real-time EGX (Egyptian Exchange) stock data from Mubasher.
 * No API key required — public endpoint.
 * Endpoint: GET https://www.mubasher.info/api/1/stocks/prices?country=eg
 */
class YahooFinanceService {

    private val baseUrl = "https://www.mubasher.info/api/1"

    private val client = HttpClient(CIO) {
        install(ContentNegotiation) {
            json(Json {
                ignoreUnknownKeys = true
                isLenient         = true
                coerceInputValues = true
            })
        }
        install(HttpTimeout) {
            requestTimeoutMillis = 15_000
            connectTimeoutMillis = 5_000
            socketTimeoutMillis  = 15_000
        }
    }

    // ── Public API ────────────────────────────────────────────────────────────

    /** Fetch all EGX stocks from Mubasher in a single call. No API key needed. */
    suspend fun fetchQuotes(): List<LiveQuote> {
        return try {
            val element: JsonElement = client.get("$baseUrl/stocks/prices?country=eg").body()
            val array = element as? JsonArray ?: return emptyList()
            array.mapNotNull { it.jsonObject.toQuoteOrNull() }
        } catch (e: Exception) {
            logger.error(e) { "fetchQuotes from Mubasher failed" }
            emptyList()
        }
    }

    /**
     * Fetch OHLCV candles for a given EGX symbol.
     * Returns empty list gracefully if the endpoint is unavailable.
     *
     * @param symbol  EGX stock code, e.g. "SWDY"
     * @param period  "1d" | "5d" | "1mo" | "3mo" | "6mo" | "1y" | "5y"
     */
    suspend fun fetchCandles(symbol: String, period: String): List<Candle> {
        return try {
            val (interval, from) = periodToParams(period)
            val url = "$baseUrl/stocks/ohlcv?code=$symbol&country=eg&interval=$interval&from=$from"
            val element: JsonElement = client.get(url).body()
            val array = element as? JsonArray ?: return emptyList()
            array.mapNotNull { it.jsonObject.toCandleOrNull() }
        } catch (e: Exception) {
            logger.warn { "fetchCandles($symbol, $period) unavailable — candle endpoint may differ" }
            emptyList()
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private fun periodToParams(period: String): Pair<String, String> {
        val today = java.time.LocalDate.now()
        val (interval, daysBack) = when (period) {
            "1d"  -> "5min"  to 1
            "5d"  -> "1hour" to 5
            "1mo" -> "1day"  to 30
            "3mo" -> "1day"  to 90
            "6mo" -> "1day"  to 180
            "1y"  -> "1day"  to 365
            "5y"  -> "1week" to 1825
            else  -> "1day"  to 90
        }
        return interval to today.minusDays(daysBack.toLong()).toString()
    }

    /**
     * Mubasher returns numbers as strings with commas and percentage with % sign:
     *   "value": "107.00", "volume": "3,469,400", "changePercentage": "11.98%"
     */
    private fun JsonObject.toQuoteOrNull(): LiveQuote? {
        val code  = this["code"]?.jsonPrimitive?.contentOrNull ?: return null
        val price = this["value"]?.jsonPrimitive?.contentOrNull
            ?.replace(",", "")?.toDoubleOrNull() ?: return null

        val changePerc = this["changePercentage"]?.jsonPrimitive?.contentOrNull
            ?.replace("%", "")?.trim()?.toDoubleOrNull() ?: 0.0
        val change = this["change"]?.jsonPrimitive?.contentOrNull
            ?.replace(",", "")?.toDoubleOrNull() ?: 0.0
        val volume = this["volume"]?.jsonPrimitive?.contentOrNull
            ?.replace(",", "")?.toLongOrNull() ?: 0L

        return LiveQuote(
            symbol        = code,
            name          = this["name"]?.jsonPrimitive?.contentOrNull ?: code,
            exchange      = this["exchange"]?.jsonPrimitive?.contentOrNull ?: "EGX",
            price         = price,
            change        = change,
            changePercent = changePerc,
            open          = this["open"]?.jsonPrimitive?.contentOrNull?.replace(",","")?.toDoubleOrNull(),
            high          = this["high"]?.jsonPrimitive?.contentOrNull?.replace(",","")?.toDoubleOrNull(),
            low           = this["low"]?.jsonPrimitive?.contentOrNull?.replace(",","")?.toDoubleOrNull(),
            prevClose     = null,
            volume        = volume,
            marketCap     = null
        )
    }

    /** Try common OHLCV field names — adjust once actual Mubasher candle format is known. */
    private fun JsonObject.toCandleOrNull(): Candle? {
        val time = this["t"]?.jsonPrimitive?.longOrNull
            ?: this["timestamp"]?.jsonPrimitive?.longOrNull
            ?: return null
        val open  = this["o"]?.jsonPrimitive?.doubleOrNull
            ?: this["open"]?.jsonPrimitive?.contentOrNull?.replace(",","")?.toDoubleOrNull()
            ?: return null
        val high  = this["h"]?.jsonPrimitive?.doubleOrNull
            ?: this["high"]?.jsonPrimitive?.contentOrNull?.replace(",","")?.toDoubleOrNull()
            ?: return null
        val low   = this["l"]?.jsonPrimitive?.doubleOrNull
            ?: this["low"]?.jsonPrimitive?.contentOrNull?.replace(",","")?.toDoubleOrNull()
            ?: return null
        val close = this["c"]?.jsonPrimitive?.doubleOrNull
            ?: this["close"]?.jsonPrimitive?.contentOrNull?.replace(",","")?.toDoubleOrNull()
            ?: return null
        val volume = this["v"]?.jsonPrimitive?.longOrNull
            ?: this["volume"]?.jsonPrimitive?.contentOrNull?.replace(",","")?.toLongOrNull()
            ?: 0L
        return Candle(time, open, high, low, close, volume)
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
    val timestamp: Long,
    val open:      Double,
    val high:      Double,
    val low:       Double,
    val close:     Double,
    val volume:    Long
)
