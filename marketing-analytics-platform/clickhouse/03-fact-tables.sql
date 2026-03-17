-- FACT TABLE: Unified event store (MergeTree)

CREATE DATABASE IF NOT EXISTS marketing;

CREATE TABLE IF NOT EXISTS marketing.fact_events (
    event_id        String,
    user_id         UInt64,
    user_sk         Int64,
    event_type      LowCardinality(String),
    event_source    LowCardinality(String),
    product_id      Nullable(UInt32),
    product_sk      Nullable(Int64),
    campaign_id     Nullable(UInt32),
    campaign_sk     Nullable(Int64),
    revenue         Decimal(18,2) DEFAULT 0,
    cost            Decimal(18,2) DEFAULT 0,
    channel         LowCardinality(String) DEFAULT '',
    page_url        String DEFAULT '',
    session_id      String DEFAULT '',
    event_timestamp DateTime64(3),
    event_date      Date DEFAULT toDate(event_timestamp)
) ENGINE = MergeTree()
PARTITION BY toYYYYMM(event_date)
ORDER BY (event_source, event_type, user_id, event_timestamp)
PRIMARY KEY (event_source, event_type, user_id);

-- MVs: raw_* -> fact_events (unification layer, resolve surrogate keys via point-in-time lookup)
-- ClickHouse JOIN ON allows only equality; range (valid_from/valid_to) is applied in WHERE, then GROUP BY collapses to one row per event.

CREATE MATERIALIZED VIEW IF NOT EXISTS marketing.mv_website_to_fact TO marketing.fact_events AS
SELECT
    any(r.event_id) AS event_id,
    any(r.user_id) AS user_id,
    coalesce(any(u.user_sk), toInt64(0)) AS user_sk,
    any(r.event_type) AS event_type,
    'website' AS event_source,
    any(r.product_id) AS product_id,
    any(p.product_sk) AS product_sk,
    CAST(NULL AS Nullable(UInt32)) AS campaign_id,
    CAST(NULL AS Nullable(Int64)) AS campaign_sk,
    coalesce(any(r.revenue), toDecimal64(0, 2)) AS revenue,
    toDecimal64(0, 2) AS cost,
    '' AS channel,
    any(r.page_url) AS page_url,
    any(r.session_id) AS session_id,
    any(r.timestamp) AS event_timestamp,
    toDate(any(r.timestamp)) AS event_date
FROM marketing.raw_website_events AS r
LEFT JOIN marketing.dim_users AS u ON r.user_id = u.user_id
LEFT JOIN marketing.dim_products AS p ON r.product_id = p.product_id
WHERE (u.user_sk IS NULL OR (r.timestamp >= u.valid_from AND r.timestamp < u.valid_to))
  AND (p.product_sk IS NULL OR r.product_id IS NULL OR (r.timestamp >= p.valid_from AND r.timestamp < p.valid_to))
GROUP BY r.event_id, r.user_id, r.event_type, r.timestamp, r.product_id, r.revenue, r.page_url, r.session_id;

CREATE MATERIALIZED VIEW IF NOT EXISTS marketing.mv_ad_to_fact TO marketing.fact_events AS
SELECT
    any(r.event_id) AS event_id,
    coalesce(any(r.user_id), toUInt64(0)) AS user_id,
    coalesce(any(u.user_sk), toInt64(0)) AS user_sk,
    any(r.event_type) AS event_type,
    'ad_platform' AS event_source,
    CAST(NULL AS Nullable(UInt32)) AS product_id,
    CAST(NULL AS Nullable(Int64)) AS product_sk,
    CAST(any(r.campaign_id) AS Nullable(UInt32)) AS campaign_id,
    any(c.campaign_sk) AS campaign_sk,
    coalesce(any(r.revenue), toDecimal64(0, 2)) AS revenue,
    any(r.cost) AS cost,
    any(r.platform) AS channel,
    '' AS page_url,
    '' AS session_id,
    any(r.timestamp) AS event_timestamp,
    toDate(any(r.timestamp)) AS event_date
FROM marketing.raw_ad_events AS r
LEFT JOIN marketing.dim_users AS u ON r.user_id = u.user_id
LEFT JOIN marketing.dim_campaigns AS c ON r.campaign_id = c.campaign_id
WHERE (u.user_sk IS NULL OR (r.timestamp >= u.valid_from AND r.timestamp < u.valid_to))
  AND (c.campaign_sk IS NULL OR (r.timestamp >= c.valid_from AND r.timestamp < c.valid_to))
GROUP BY r.event_id, r.user_id, r.event_type, r.campaign_id, r.revenue, r.cost, r.platform, r.timestamp;

CREATE MATERIALIZED VIEW IF NOT EXISTS marketing.mv_backend_to_fact TO marketing.fact_events AS
SELECT
    any(r.event_id) AS event_id,
    any(r.user_id) AS user_id,
    coalesce(any(u.user_sk), toInt64(0)) AS user_sk,
    any(r.event_type) AS event_type,
    'backend' AS event_source,
    any(r.product_id) AS product_id,
    any(p.product_sk) AS product_sk,
    CAST(NULL AS Nullable(UInt32)) AS campaign_id,
    CAST(NULL AS Nullable(Int64)) AS campaign_sk,
    coalesce(any(r.amount), toDecimal64(0, 2)) AS revenue,
    toDecimal64(0, 2) AS cost,
    '' AS channel,
    '' AS page_url,
    '' AS session_id,
    any(r.timestamp) AS event_timestamp,
    toDate(any(r.timestamp)) AS event_date
FROM marketing.raw_backend_events AS r
LEFT JOIN marketing.dim_users AS u ON r.user_id = u.user_id
LEFT JOIN marketing.dim_products AS p ON r.product_id = p.product_id
WHERE (u.user_sk IS NULL OR (r.timestamp >= u.valid_from AND r.timestamp < u.valid_to))
  AND (p.product_sk IS NULL OR r.product_id IS NULL OR (r.timestamp >= p.valid_from AND r.timestamp < p.valid_to))
GROUP BY r.event_id, r.user_id, r.event_type, r.product_id, r.amount, r.timestamp;
