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
 * - Returns real-time quotes + full candle history in one API call per request
 * - Reliable enough for development and moderate production load
 *
 * For production at scale, swap this implementation with a paid provider
 * (Polygon.io, Finnhub, Bloomberg) — callers only depend on [LiveQuote] and
 * [Candle] data classes, not this class directly.
 */
class YahooFinanceService {

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
     * Returns an empty list on any error — callers handle that gracefully.
     */
    suspend fun fetchQuotes(symbols: List<String>): List<LiveQuote> {
        if (symbols.isEmpty()) return emptyList()
        val joined = symbols.joinToString(",")
        val fields = "symbol,shortName,fullExchangeName," +
            "regularMarketPrice,regularMarketChange,regularMarketChangePercent," +
            "regularMarketOpen,regularMarketDayHigh,regularMarketDayLow," +
            "regularMarketPreviousClose,regularMarketVolume,marketCap"
        val url = "https://query1.finance.yahoo.com/v7/finance/quote" +
            "?symbols=$joined&fields=$fields&lang=en-US&region=US"

        return try {
            val body: JsonObject = client.get(url) { yahooHeaders() }.body()
            body["quoteResponse"]
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
        val url = "https://query1.finance.yahoo.com/v8/finance/chart/$symbol" +
            "?interval=$interval&range=$range&lang=en-US"

        return try {
            val body: JsonObject = client.get(url) { yahooHeaders() }.body()
            parseCandles(body)
        } catch (e: Exception) {
            logger.error(e) { "fetchCandles failed: $symbol ($period)" }
            emptyList()
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    /**
     * Sets browser-like headers on every Yahoo Finance request.
     * Without these, Yahoo's bot detection can return 401 or empty results.
     */
    private fun HttpRequestBuilder.yahooHeaders() {
        header("User-Agent",
            "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) " +
            "AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
        header("Accept",          "application/json, text/plain, */*")
        header("Accept-Language", "en-US,en;q=0.9")
        header("Origin",          "https://finance.yahoo.com")
        header("Referer",         "https://finance.yahoo.com/")
    }

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
     * Skips any bar where any OHLC value is null (incomplete intraday bar).
     */
    private fun parseCandles(json: JsonObject): List<Candle> {
        val result = json["chart"]
            ?.jsonObject?.get("result")
            ?.jsonArray?.firstOrNull()?.jsonObject
            ?: return emptyList()

        val timestamps = result["timestamp"]?.jsonArray
            ?.mapNotNull { it.jsonPrimitive.longOrNull }
            ?: return emptyList()

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
            val open   = opens?.getOrNull(i)?.jsonPrimitive?.doubleOrNull   ?: return@mapNotNull null
            val high   = highs?.getOrNull(i)?.jsonPrimitive?.doubleOrNull   ?: return@mapNotNull null
            val low    = lows?.getOrNull(i)?.jsonPrimitive?.doubleOrNull    ?: return@mapNotNull null
            val close  = closes?.getOrNull(i)?.jsonPrimitive?.doubleOrNull  ?: return@mapNotNull null
            val volume = volumes?.getOrNull(i)?.jsonPrimitive?.longOrNull   ?: 0L
            Candle(timestamps[i], open, high, low, close, volume)
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
