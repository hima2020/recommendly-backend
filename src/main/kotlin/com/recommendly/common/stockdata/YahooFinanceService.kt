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
 * Fetches real-time stock quotes and OHLCV candle data from Yahoo Finance.
 *
 * Why Yahoo Finance?
 * - No API key required for basic market data
 * - Returns real-time quotes + full candle history
 * - Reliable enough for development and moderate production load
 *
 * For production at scale, swap this implementation with a paid provider
 * (Polygon.io, Finnhub, Bloomberg) without changing any callers — they all
 * depend on [LiveQuote] and [Candle] data classes, not this class directly.
 */
class YahooFinanceService {

    private val client = HttpClient(CIO) {
        install(ContentNegotiation) {
            json(Json {
                ignoreUnknownKeys = true
                isLenient = true
                coerceInputValues = true
            })
        }
        install(HttpTimeout) {
            requestTimeoutMillis  = 8_000
            connectTimeoutMillis  = 4_000
            socketTimeoutMillis   = 8_000
        }
    }

    // Browser-like headers to avoid being blocked by Yahoo's bot detection
    private val commonHeaders: HeadersBuilder.() -> Unit = {
        append("User-Agent", "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) " +
            "AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
        append("Accept", "application/json, text/plain, */*")
        append("Accept-Language", "en-US,en;q=0.9")
        append("Origin", "https://finance.yahoo.com")
        append("Referer", "https://finance.yahoo.com/")
    }

    // ── Public API ────────────────────────────────────────────────────────────

    /**
     * Fetches real-time quotes for up to 50 symbols in a single HTTP call.
     * Returns an empty list on any error — callers should handle that gracefully.
     */
    suspend fun fetchQuotes(symbols: List<String>): List<LiveQuote> {
        if (symbols.isEmpty()) return emptyList()
        val joined = symbols.joinToString(",")
        return try {
            val fields = listOf(
                "symbol", "shortName", "fullExchangeName",
                "regularMarketPrice", "regularMarketChange",
                "regularMarketChangePercent", "regularMarketOpen",
                "regularMarketDayHigh", "regularMarketDayLow",
                "regularMarketPreviousClose", "regularMarketVolume", "marketCap"
            ).joinToString(",")

            val url = "https://query1.finance.yahoo.com/v7/finance/quote" +
                "?symbols=$joined&fields=$fields&lang=en-US&region=US"

            val json: JsonObject = client.get(url) { headers(commonHeaders) }.body()

            json["quoteResponse"]
                ?.jsonObject?.get("result")
                ?.jsonArray
                ?.mapNotNull { it.jsonObject.toLiveQuoteOrNull() }
                ?: emptyList()

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
     */
    suspend fun fetchCandles(symbol: String, period: String): List<Candle> {
        val (interval, range) = periodToParams(period)
        return try {
            val url = "https://query1.finance.yahoo.com/v8/finance/chart/$symbol" +
                "?interval=$interval&range=$range&lang=en-US"

            val json: JsonObject = client.get(url) { headers(commonHeaders) }.body()
            parseCandles(json)

        } catch (e: Exception) {
            logger.error(e) { "fetchCandles failed for: $symbol ($period)" }
            emptyList()
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    /** Maps human-readable period to Yahoo Finance interval + range params. */
    private fun periodToParams(period: String): Pair<String, String> = when (period) {
        "1d"  -> "5m"  to "1d"
        "5d"  -> "30m" to "5d"
        "1mo" -> "1d"  to "1mo"
        "3mo" -> "1d"  to "3mo"
        "6mo" -> "1wk" to "6mo"
        "1y"  -> "1wk" to "1y"
        "5y"  -> "1mo" to "5y"
        else  -> "1d"  to "3mo"
    }

    /**
     * Parses the nested chart API response into a flat list of candles.
     * Skips any candle where any OHLC field is null (incomplete intraday bar).
     */
    private fun parseCandles(json: JsonObject): List<Candle> {
        val result = json["chart"]
            ?.jsonObject?.get("result")
            ?.jsonArray?.firstOrNull()?.jsonObject
            ?: return emptyList()

        val timestamps = result["timestamp"]?.jsonArray?.mapNotNull {
            it.jsonPrimitive.longOrNull
        } ?: return emptyList()

        val quoteBlock = result["indicators"]
            ?.jsonObject?.get("quote")
            ?.jsonArray?.firstOrNull()?.jsonObject
            ?: return emptyList()

        val opens   = quoteBlock["open"]?.jsonArray
        val highs   = quoteBlock["high"]?.jsonArray
        val lows    = quoteBlock["low"]?.jsonArray
        val closes  = quoteBlock["close"]?.jsonArray
        val volumes = quoteBlock["volume"]?.jsonArray

        return timestamps.indices.mapNotNull { i ->
            val open   = opens?.get(i)?.jsonPrimitive?.doubleOrNull   ?: return@mapNotNull null
            val high   = highs?.get(i)?.jsonPrimitive?.doubleOrNull   ?: return@mapNotNull null
            val low    = lows?.get(i)?.jsonPrimitive?.doubleOrNull    ?: return@mapNotNull null
            val close  = closes?.get(i)?.jsonPrimitive?.doubleOrNull  ?: return@mapNotNull null
            val volume = volumes?.get(i)?.jsonPrimitive?.longOrNull   ?: 0L
            Candle(timestamp = timestamps[i], open, high, low, close, volume)
        }
    }

    private fun JsonObject.toLiveQuoteOrNull(): LiveQuote? {
        val symbol = this["symbol"]?.jsonPrimitive?.contentOrNull ?: return null
        val price  = this["regularMarketPrice"]?.jsonPrimitive?.doubleOrNull ?: return null
        return LiveQuote(
            symbol        = symbol,
            name          = this["shortName"]?.jsonPrimitive?.contentOrNull ?: symbol,
            exchange      = this["fullExchangeName"]?.jsonPrimitive?.contentOrNull ?: "",
            price         = price,
            change        = this["regularMarketChange"]?.jsonPrimitive?.doubleOrNull ?: 0.0,
            changePercent = this["regularMarketChangePercent"]?.jsonPrimitive?.doubleOrNull ?: 0.0,
            open          = this["regularMarketOpen"]?.jsonPrimitive?.doubleOrNull,
            high          = this["regularMarketDayHigh"]?.jsonPrimitive?.doubleOrNull,
            low           = this["regularMarketDayLow"]?.jsonPrimitive?.doubleOrNull,
            prevClose     = this["regularMarketPreviousClose"]?.jsonPrimitive?.doubleOrNull,
            volume        = this["regularMarketVolume"]?.jsonPrimitive?.longOrNull ?: 0L,
            marketCap     = this["marketCap"]?.jsonPrimitive?.longOrNull
        )
    }
}

// ── Domain models (internal — never serialized directly to API responses) ─────

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
    val timestamp: Long,   // Unix epoch in SECONDS (as returned by Yahoo Finance)
    val open:      Double,
    val high:      Double,
    val low:       Double,
    val close:     Double,
    val volume:    Long
)
