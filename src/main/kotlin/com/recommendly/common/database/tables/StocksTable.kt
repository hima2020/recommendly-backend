package com.recommendly.common.database.tables

import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.kotlin.datetime.timestampWithTimeZone

/**
 * Mirrors the `stocks` table created in V3__create_stocks_table.sql.
 *
 * Symbol is the natural primary key (e.g. "AAPL") — using a varchar PK here
 * avoids a JOIN on every lookup and keeps queries readable.
 *
 * Prices / market cap are snapshots — they'll be refreshed by a data-feed job
 * in a later phase. For now the seed values give us real data to test against.
 */
object StocksTable : Table("stocks") {
    val symbol        = varchar("symbol", 10)
    val name          = varchar("name", 255)
    val exchange      = varchar("exchange", 50)
    val sector        = varchar("sector", 100).nullable()
    val currentPrice  = decimal("current_price", 18, 4).nullable()
    val marketCap     = long("market_cap").nullable()
    val peRatio       = decimal("pe_ratio", 10, 2).nullable()
    val changePercent = decimal("change_percent", 8, 4).nullable()
    val isActive      = bool("is_active").default(true)
    val createdAt     = timestampWithTimeZone("created_at")
    val updatedAt     = timestampWithTimeZone("updated_at")

    override val primaryKey = PrimaryKey(symbol)
}
