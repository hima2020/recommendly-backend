-- ─────────────────────────────────────────────────────────────────────────────
-- V3: Stocks table
--
-- Stores a snapshot of stock data for every instrument the app tracks.
-- Prices/market caps are updated periodically by a data-feed job (Phase 5+).
-- For now, the seed data below gives us real symbols to develop against.
-- ─────────────────────────────────────────────────────────────────────────────

CREATE TABLE stocks (
    symbol          VARCHAR(10)      PRIMARY KEY,
    name            VARCHAR(255)     NOT NULL,
    exchange        VARCHAR(50)      NOT NULL,
    sector          VARCHAR(100),
    current_price   DECIMAL(18, 4),
    market_cap      BIGINT,
    pe_ratio        DECIMAL(10, 2),
    change_percent  DECIMAL(8, 4)    DEFAULT 0,    -- 24h price change %
    is_active       BOOLEAN          NOT NULL DEFAULT TRUE,
    created_at      TIMESTAMPTZ      NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ      NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_stocks_exchange  ON stocks(exchange);
CREATE INDEX idx_stocks_sector    ON stocks(sector);
CREATE INDEX idx_stocks_is_active ON stocks(is_active);
-- B-tree index on name prefix searches (works without pg_trgm superuser privilege)
CREATE INDEX idx_stocks_name      ON stocks(name);

-- ─────────────────────────────────────────────────────────────────────────────
-- Seed data — a representative set of well-known stocks across sectors.
-- Prices are approximate at time of writing (not real-time).
-- ─────────────────────────────────────────────────────────────────────────────

INSERT INTO stocks (symbol, name, exchange, sector, current_price, market_cap, pe_ratio, change_percent) VALUES
-- Technology
('AAPL',  'Apple Inc.',                        'NASDAQ', 'Technology',      193.50, 2980000000000,  32.5,  0.85),
('MSFT',  'Microsoft Corporation',             'NASDAQ', 'Technology',      415.20, 3080000000000,  36.1,  1.12),
('GOOGL', 'Alphabet Inc.',                     'NASDAQ', 'Technology',      175.30, 2190000000000,  26.8, -0.43),
('META',  'Meta Platforms Inc.',               'NASDAQ', 'Technology',      490.10, 1250000000000,  27.4,  2.15),
('NVDA',  'NVIDIA Corporation',                'NASDAQ', 'Technology',      875.40, 2160000000000,  70.2,  3.40),
('AMZN',  'Amazon.com Inc.',                   'NASDAQ', 'Technology',      188.20, 1970000000000,  42.3,  0.67),
('TSLA',  'Tesla Inc.',                        'NASDAQ', 'Technology',      177.80,  566000000000,  48.9, -1.85),
('INTC',  'Intel Corporation',                 'NASDAQ', 'Technology',       32.10,  136000000000,  12.1, -0.52),
('AMD',   'Advanced Micro Devices Inc.',       'NASDAQ', 'Technology',      171.40,  278000000000,  45.6,  1.23),
('ORCL',  'Oracle Corporation',                'NYSE',   'Technology',      125.60,  347000000000,  30.2,  0.31),
-- Financials
('JPM',   'JPMorgan Chase & Co.',              'NYSE',   'Financials',      195.40,  562000000000,  12.4,  0.48),
('BAC',   'Bank of America Corporation',       'NYSE',   'Financials',       38.20,  302000000000,  11.8, -0.26),
('GS',    'The Goldman Sachs Group Inc.',      'NYSE',   'Financials',      462.80,  154000000000,  16.3,  0.92),
('V',     'Visa Inc.',                         'NYSE',   'Financials',      273.50,  571000000000,  29.7,  0.63),
('MA',    'Mastercard Incorporated',           'NYSE',   'Financials',      463.20,  432000000000,  34.1,  0.74),
-- Healthcare
('JNJ',   'Johnson & Johnson',                 'NYSE',   'Healthcare',      152.30,  366000000000,  15.6, -0.15),
('UNH',   'UnitedHealth Group Incorporated',   'NYSE',   'Healthcare',      520.40,  479000000000,  22.3,  0.38),
('PFE',   'Pfizer Inc.',                       'NYSE',   'Healthcare',       27.60,  156000000000,   9.8, -0.72),
('ABBV',  'AbbVie Inc.',                       'NYSE',   'Healthcare',      168.70,  297000000000,  26.4,  1.05),
('MRK',   'Merck & Co. Inc.',                  'NYSE',   'Healthcare',      128.90,  326000000000,  17.8,  0.22),
-- Consumer
('WMT',   'Walmart Inc.',                      'NYSE',   'Consumer',         67.40,  543000000000,  28.9,  0.55),
('HD',    'The Home Depot Inc.',               'NYSE',   'Consumer',        355.20,  352000000000,  23.1,  0.18),
('NKE',   'NIKE Inc.',                         'NYSE',   'Consumer',         94.30,  143000000000,  25.6, -0.84),
('SBUX',  'Starbucks Corporation',             'NASDAQ', 'Consumer',         79.60,   89000000000,  24.2, -0.35),
-- Energy
('XOM',   'Exxon Mobil Corporation',           'NYSE',   'Energy',          113.40,  454000000000,  14.2,  0.96),
('CVX',   'Chevron Corporation',               'NYSE',   'Energy',          155.80,  297000000000,  13.8,  0.41),
('COP',   'ConocoPhillips',                    'NYSE',   'Energy',          113.20,  139000000000,  12.9, -0.17),
-- Telecom
('T',     'AT&T Inc.',                         'NYSE',   'Telecom',          17.30,  123000000000,  10.2, -0.58),
('VZ',    'Verizon Communications Inc.',       'NYSE',   'Telecom',          40.10,  168000000000,   9.7, -0.31)
ON CONFLICT (symbol) DO NOTHING;
