package com.recommendly.api.stocks

import kotlinx.serialization.Serializable

/**
 * Returned by GET /api/v1/stocks (listing / search).
 * Lean DTO — no PE ratio or market cap to keep list responses small.
 */
@Serializable
data class StockSummaryDto(
    val symbol:        String,
    val name:          String,
    val exchange:      String,
    val sector:        String?,
    val currentPrice:  Double?,
    val changePercent: Double?   // 24-hour price change, e.g. 1.25 means +1.25%
)

/**
 * Returned by GET /api/v1/stocks/{symbol} (full detail).
 * Includes all fields including valuation metrics.
 */
@Serializable
data class StockDetailDto(
    val symbol:        String,
    val name:          String,
    val exchange:      String,
    val sector:        String?,
    val currentPrice:  Double?,
    val marketCap:     Long?,
    val peRatio:       Double?,
    val changePercent: Double?
)

/**
 * Wrapper for paginated list responses.
 * All list endpoints use this shape so the client always knows the total count.
 */
@Serializable
data class PagedResponse<T>(
    val data:   List<T>,
    val total:  Long,
    val limit:  Int,
    val offset: Int
)
