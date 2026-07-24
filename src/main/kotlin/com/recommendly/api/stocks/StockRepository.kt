package com.recommendly.api.stocks

import com.recommendly.common.database.dbQuery
import com.recommendly.common.database.tables.StocksTable
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.SqlExpressionBuilder.like

/**
 * Data access layer for stocks.
 *
 * Search uses ILIKE (case-insensitive pattern match) on symbol and name.
 * For full trigram/fuzzy search the pg_trgm index created in V3 would be
 * used automatically by Postgres when the pattern isn't purely prefix-based.
 */
class StockRepository {

    /**
     * Returns a paginated, optionally filtered list of active stocks.
     *
     * @param search  Optional substring to match against symbol or company name (case-insensitive)
     * @param sector  Optional exact sector filter
     * @param limit   Max rows to return (capped at 100 in the service layer)
     * @param offset  Number of rows to skip (for pagination)
     */
    suspend fun findAll(
        search: String? = null,
        sector: String? = null,
        limit:  Int     = 20,
        offset: Int     = 0
    ): Pair<List<StockSummaryDto>, Long> = dbQuery {

        val baseQuery = StocksTable
            .selectAll()
            .where { buildFilter(search, sector) }
            .orderBy(StocksTable.symbol, SortOrder.ASC)

        val total = baseQuery.count()
        val rows  = baseQuery.limit(limit, offset.toLong()).map { it.toSummaryDto() }
        Pair(rows, total)
    }

    /** Returns a single stock by its ticker symbol, or null if not found. */
    suspend fun findBySymbol(symbol: String): StockDetailDto? = dbQuery {
        StocksTable
            .selectAll()
            .where { StocksTable.symbol eq symbol.uppercase() }
            .singleOrNull()
            ?.toDetailDto()
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    /** Builds the WHERE clause from optional filter params. */
    private fun buildFilter(search: String?, sector: String?): Op<Boolean> {
        var condition: Op<Boolean> = StocksTable.isActive eq true

        if (!search.isNullOrBlank()) {
            val pattern = "%${search.trim()}%"
            condition = condition and (
                (StocksTable.symbol  like pattern) or
                (StocksTable.name    like pattern)
            )
        }

        if (!sector.isNullOrBlank()) {
            condition = condition and (StocksTable.sector eq sector.trim())
        }

        return condition
    }

    private fun ResultRow.toSummaryDto() = StockSummaryDto(
        symbol        = this[StocksTable.symbol],
        name          = this[StocksTable.name],
        exchange      = this[StocksTable.exchange],
        sector        = this[StocksTable.sector],
        currentPrice  = this[StocksTable.currentPrice]?.toDouble(),
        changePercent = this[StocksTable.changePercent]?.toDouble()
    )

    private fun ResultRow.toDetailDto() = StockDetailDto(
        symbol        = this[StocksTable.symbol],
        name          = this[StocksTable.name],
        exchange      = this[StocksTable.exchange],
        sector        = this[StocksTable.sector],
        currentPrice  = this[StocksTable.currentPrice]?.toDouble(),
        marketCap     = this[StocksTable.marketCap],
        peRatio       = this[StocksTable.peRatio]?.toDouble(),
        changePercent = this[StocksTable.changePercent]?.toDouble()
    )
}
