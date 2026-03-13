-- FACT TABLE: Unified event store (MergeTree)

CREATE DATABASE IF NOT EXISTS marketing;

CREATE TABLE IF NOT EXISTS marketing.fact_events (
    event_id        String,
    user_id         UInt64,
    event_type      LowCardinality(String),
    event_source    LowCardinality(String),
    product_id      Nullable(UInt32),
    campaign_id     Nullable(UInt32),
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

-- MVs: raw_* -> fact_events (unification layer)

CREATE MATERIALIZED VIEW IF NOT EXISTS marketing.mv_website_to_fact TO marketing.fact_events AS
SELECT
    event_id,
    user_id,
    event_type,
    'website' AS event_source,
    product_id,
    CAST(NULL AS Nullable(UInt32)) AS campaign_id,
    coalesce(revenue, toDecimal64(0, 2)) AS revenue,
    toDecimal64(0, 2) AS cost,
    '' AS channel,
    page_url,
    session_id,
    timestamp AS event_timestamp,
    toDate(timestamp) AS event_date
FROM marketing.raw_website_events;

CREATE MATERIALIZED VIEW IF NOT EXISTS marketing.mv_ad_to_fact TO marketing.fact_events AS
SELECT
    event_id,
    coalesce(user_id, toUInt64(0)) AS user_id,
    event_type,
    'ad_platform' AS event_source,
    CAST(NULL AS Nullable(UInt32)) AS product_id,
    CAST(campaign_id AS Nullable(UInt32)) AS campaign_id,
    coalesce(revenue, toDecimal64(0, 2)) AS revenue,
    cost,
    platform AS channel,
    '' AS page_url,
    '' AS session_id,
    timestamp AS event_timestamp,
    toDate(timestamp) AS event_date
FROM marketing.raw_ad_events;

CREATE MATERIALIZED VIEW IF NOT EXISTS marketing.mv_backend_to_fact TO marketing.fact_events AS
SELECT
    event_id,
    user_id,
    event_type,
    'backend' AS event_source,
    product_id,
    CAST(NULL AS Nullable(UInt32)) AS campaign_id,
    coalesce(amount, toDecimal64(0, 2)) AS revenue,
    toDecimal64(0, 2) AS cost,
    '' AS channel,
    '' AS page_url,
    '' AS session_id,
    timestamp AS event_timestamp,
    toDate(timestamp) AS event_date
FROM marketing.raw_backend_events;
