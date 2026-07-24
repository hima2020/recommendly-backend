package com.recommendly.api.stocks

import io.ktor.http.*
import io.ktor.server.response.*
import io.ktor.server.routing.*

/**
 * Stock market routes — all public (no JWT required).
 *
 * GET  /api/v1/stocks                        — live quotes for all tracked symbols
 * GET  /api/v1/stocks/{symbol}/quote         — live quote for one symbol
 * GET  /api/v1/stocks/{symbol}/candles       — OHLCV candle data for chart
 *
 * Query params for candles:
 *   period  — one of: 1d | 5d | 1mo | 3mo | 6mo | 1y | 5y  (default: 3mo)
 *
 * All responses are cached in Redis:
 *   quotes  → 60 seconds
 *   candles → 5 min (intraday) or 15 min (daily/weekly)
 */
fun Route.stockRoutes(stockService: StockService) {

    route("/stocks") {

        // ── GET /api/v1/stocks ────────────────────────────────────────────────
        // Live quotes for every tracked symbol in one response.
        // Clients use this to populate the ticker bar and stock grid.
        get {
            val quotes = stockService.getAllQuotes()
            call.respond(HttpStatusCode.OK, quotes)
        }

        // ── GET /api/v1/stocks/{symbol}/quote ─────────────────────────────────
        // Full real-time quote for a single ticker — open, high, low, volume, etc.
        get("/{symbol}/quote") {
            val symbol = call.parameters["symbol"] ?: ""
            val quote  = stockService.getQuote(symbol)
            call.respond(HttpStatusCode.OK, quote)
        }

        // ── GET /api/v1/stocks/{symbol}/candles ───────────────────────────────
        // OHLCV candle history. period controls time range + bar interval.
        get("/{symbol}/candles") {
            val symbol = call.parameters["symbol"] ?: ""
            val period = call.request.queryParameters["period"] ?: "3mo"
            val candles = stockService.getCandles(symbol, period)
            call.respond(HttpStatusCode.OK, candles)
        }
    }
}
