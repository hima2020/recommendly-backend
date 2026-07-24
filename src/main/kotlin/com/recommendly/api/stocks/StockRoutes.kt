package com.recommendly.api.stocks

import io.ktor.http.*
import io.ktor.server.response.*
import io.ktor.server.routing.*

/**
 * Stock market routes — intentionally public (no JWT required).
 *
 * GET /api/v1/stocks               — paginated list with optional search/filter
 * GET /api/v1/stocks/{symbol}      — full detail for a single stock
 *
 * Query params for listing:
 *   search  — case-insensitive substring match on symbol or company name
 *   sector  — exact sector name (Technology, Financials, Healthcare, etc.)
 *   limit   — page size, default 20, max 100
 *   offset  — rows to skip, default 0
 *
 * Example:
 *   GET /api/v1/stocks?search=apple&limit=5
 *   GET /api/v1/stocks?sector=Technology&limit=10&offset=10
 *   GET /api/v1/stocks/AAPL
 */
fun Route.stockRoutes(stockService: StockService) {

    route("/stocks") {

        // ── GET /api/v1/stocks ────────────────────────────────────────────────
        get {
            val search = call.request.queryParameters["search"]?.takeIf { it.isNotBlank() }
            val sector = call.request.queryParameters["sector"]?.takeIf { it.isNotBlank() }
            val limit  = call.request.queryParameters["limit"]?.toIntOrNull()  ?: 20
            val offset = call.request.queryParameters["offset"]?.toIntOrNull() ?: 0

            val result = stockService.listStocks(search, sector, limit, offset)
            call.respond(HttpStatusCode.OK, result)
        }

        // ── GET /api/v1/stocks/{symbol} ───────────────────────────────────────
        get("/{symbol}") {
            val symbol = call.parameters["symbol"] ?: ""
            val stock  = stockService.getStock(symbol)
            call.respond(HttpStatusCode.OK, stock)
        }
    }
}
