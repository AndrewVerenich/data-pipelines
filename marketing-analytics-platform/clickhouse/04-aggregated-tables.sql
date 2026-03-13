-- AGGREGATED TABLES

CREATE DATABASE IF NOT EXISTS marketing;

-- 1) Daily User Activity (SummingMergeTree)
CREATE TABLE IF NOT EXISTS marketing.daily_user_activity (
    event_date     Date,
    event_source   LowCardinality(String),
    event_type     LowCardinality(String),
    events_count   UInt64,
    unique_users   UInt64,
    total_revenue  Decimal(38,2)
) ENGINE = SummingMergeTree((events_count, unique_users, total_revenue))
PARTITION BY toYYYYMM(event_date)
ORDER BY (event_date, event_source, event_type);

-- 2) Campaign Performance Daily (SummingMergeTree)
CREATE TABLE IF NOT EXISTS marketing.campaign_performance_daily (
    event_date     Date,
    campaign_id    UInt32,
    platform       LowCardinality(String),
    impressions    UInt64,
    clicks         UInt64,
    conversions    UInt64,
    total_cost     Decimal(38,2),
    total_revenue  Decimal(38,2)
) ENGINE = SummingMergeTree((impressions, clicks, conversions, total_cost, total_revenue))
PARTITION BY toYYYYMM(event_date)
ORDER BY (event_date, campaign_id, platform);

-- 3) Conversion Funnel Daily (AggregatingMergeTree)
CREATE TABLE IF NOT EXISTS marketing.conversion_funnel_daily (
    event_date   Date,
    page_viewers AggregateFunction(uniq, UInt64),
    clickers     AggregateFunction(uniq, UInt64),
    cart_adders  AggregateFunction(uniq, UInt64),
    purchasers   AggregateFunction(uniq, UInt64)
) ENGINE = AggregatingMergeTree()
PARTITION BY toYYYYMM(event_date)
ORDER BY event_date;

-- 4) User LTV (AggregatingMergeTree)
CREATE TABLE IF NOT EXISTS marketing.user_ltv (
    user_id        UInt64,
    total_revenue  SimpleAggregateFunction(sum, Decimal(38,2)),
    order_count    SimpleAggregateFunction(sum, UInt64),
    first_purchase SimpleAggregateFunction(min, DateTime64(3)),
    last_purchase  SimpleAggregateFunction(max, DateTime64(3))
) ENGINE = AggregatingMergeTree()
ORDER BY user_id;
