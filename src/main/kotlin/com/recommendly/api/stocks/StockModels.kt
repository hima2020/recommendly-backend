package com.recommendly.api.stocks

import kotlinx.serialization.Serializable

/**
 * Current market quote for a single stock — returned by:
 *   GET /api/v1/stocks          (list, all tracked symbols)
 *   GET /api/v1/stocks/{symbol}/quote  (single symbol detail)
 */
@Serializable
data class QuoteDto(
    val symbol:        String,
    val name:          String,
    val exchange:      String,
    val price:         Double,
    val change:        Double,         // raw dollar change (e.g. +2.30)
    val changePercent: Double,         // percentage change (e.g. +1.20)
    val open:          Double?,
    val high:          Double?,
    val low:           Double?,
    val prevClose:     Double?,
    val volume:        Long,
    val marketCap:     Long?
)

/**
 * One OHLCV bar — used in the candlestick chart.
 * [time] is a Unix epoch timestamp in SECONDS (what TradingView Lightweight Charts expects).
 */
@Serializable
data class CandleDto(
    val time:   Long,
    val open:   Double,
    val high:   Double,
    val low:    Double,
    val close:  Double,
    val volume: Long
)

/**
 * Full candle-history response for one symbol + period.
 */
@Serializable
data class CandlesResponse(
    val symbol:  String,
    val period:  String,
    val candles: List<CandleDto>
)
