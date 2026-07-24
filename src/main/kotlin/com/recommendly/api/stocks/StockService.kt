package com.recommendly.api.stocks

import com.recommendly.plugins.BadRequestException
import com.recommendly.plugins.NotFoundException
import mu.KotlinLogging

private val logger = KotlinLogging.logger {}

/**
 * Business logic for stocks.
 *
 * Responsibilities:
 * - Input validation and sanitisation (limit caps, blank checks)
 * - Calling the repository and shaping the response
 *
 * The listing endpoint is intentionally public (no JWT required).
 * Users should be able to browse stocks before deciding to register.
 */
class StockService(
    private val repo: StockRepository
) {

    /**
     * Returns a paginated list of stocks.
     *
     * @param search  Optional substring filter on symbol / company name
     * @param sector  Optional exact-match sector filter
     * @param limit   Page size — capped at 100 to protect the DB
     * @param offset  Zero-based row offset for pagination
     */
    suspend fun listStocks(
        search: String?,
        sector: String?,
        limit:  Int,
        offset: Int
    ): PagedResponse<StockSummaryDto> {
        val safeLimit  = limit.coerceIn(1, 100)
        val safeOffset = offset.coerceAtLeast(0)

        val (stocks, total) = repo.findAll(search, sector, safeLimit, safeOffset)
        logger.debug { "listStocks search=$search sector=$sector → ${stocks.size}/$total" }

        return PagedResponse(
            data   = stocks,
            total  = total,
            limit  = safeLimit,
            offset = safeOffset
        )
    }

    /**
     * Returns the full detail of a single stock by ticker symbol.
     * Symbol is normalised to uppercase before the DB lookup.
     */
    suspend fun getStock(symbol: String): StockDetailDto {
        val trimmed = symbol.trim()
        if (trimmed.isBlank() || trimmed.length > 10) {
            throw BadRequestException("Invalid stock symbol")
        }
        return repo.findBySymbol(trimmed)
            ?: throw NotFoundException("Stock '$trimmed' not found")
    }
}
