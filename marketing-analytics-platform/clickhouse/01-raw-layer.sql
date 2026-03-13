-- RAW LAYER: Kafka Engine -> MergeTree via Materialized Views

CREATE DATABASE IF NOT EXISTS marketing;

-- 1) Website Events
CREATE TABLE IF NOT EXISTS marketing.kafka_website_events (
    event_id     String,
    user_id      UInt64,
    event_type   String,
    page_url     String,
    product_id   Nullable(UInt32),
    revenue      Nullable(Decimal(18,2)),
    session_id   String,
    timestamp    Float64
) ENGINE = Kafka
SETTINGS kafka_broker_list = 'kafka:9092',
         kafka_topic_list = 'marketing.website_events',
         kafka_group_name = 'ch_website_events',
         kafka_format = 'JSONEachRow',
         kafka_num_consumers = 1;

CREATE TABLE IF NOT EXISTS marketing.raw_website_events (
    event_id     String,
    user_id      UInt64,
    event_type   LowCardinality(String),
    page_url     String,
    product_id   Nullable(UInt32),
    revenue      Nullable(Decimal(18,2)),
    session_id   String,
    timestamp    DateTime64(3),
    event_date   Date DEFAULT toDate(timestamp)
) ENGINE = MergeTree()
PARTITION BY toYYYYMM(event_date)
ORDER BY (event_type, user_id, timestamp)
TTL event_date + INTERVAL 6 MONTH;

CREATE MATERIALIZED VIEW IF NOT EXISTS marketing.mv_kafka_website_events TO marketing.raw_website_events AS
SELECT
    src.event_id,
    src.user_id,
    src.event_type,
    src.page_url,
    src.product_id,
    src.revenue,
    src.session_id,
    fromUnixTimestamp64Milli(toInt64(src.timestamp * 1000)) AS timestamp,
    toDate(fromUnixTimestamp64Milli(toInt64(src.timestamp * 1000))) AS event_date
FROM marketing.kafka_website_events AS src;

-- 2) Ad Platform Events
CREATE TABLE IF NOT EXISTS marketing.kafka_ad_events (
    event_id     String,
    campaign_id  UInt32,
    platform     String,
    event_type   String,
    cost         Decimal(18,2),
    revenue      Nullable(Decimal(18,2)),
    user_id      Nullable(UInt64),
    timestamp    Float64
) ENGINE = Kafka
SETTINGS kafka_broker_list = 'kafka:9092',
         kafka_topic_list = 'marketing.ad_events',
         kafka_group_name = 'ch_ad_events',
         kafka_format = 'JSONEachRow',
         kafka_num_consumers = 1;

CREATE TABLE IF NOT EXISTS marketing.raw_ad_events (
    event_id     String,
    campaign_id  UInt32,
    platform     LowCardinality(String),
    event_type   LowCardinality(String),
    cost         Decimal(18,2),
    revenue      Nullable(Decimal(18,2)),
    user_id      Nullable(UInt64),
    timestamp    DateTime64(3),
    event_date   Date DEFAULT toDate(timestamp)
) ENGINE = MergeTree()
PARTITION BY toYYYYMM(event_date)
ORDER BY (platform, event_type, campaign_id, timestamp)
TTL event_date + INTERVAL 6 MONTH;

CREATE MATERIALIZED VIEW IF NOT EXISTS marketing.mv_kafka_ad_events TO marketing.raw_ad_events AS
SELECT
    src.event_id,
    src.campaign_id,
    src.platform,
    src.event_type,
    src.cost,
    src.revenue,
    src.user_id,
    fromUnixTimestamp64Milli(toInt64(src.timestamp * 1000)) AS timestamp,
    toDate(fromUnixTimestamp64Milli(toInt64(src.timestamp * 1000))) AS event_date
FROM marketing.kafka_ad_events AS src;

-- 3) Backend Events
CREATE TABLE IF NOT EXISTS marketing.kafka_backend_events (
    event_id     String,
    user_id      UInt64,
    event_type   String,
    order_id     Nullable(String),
    product_id   Nullable(UInt32),
    amount       Nullable(Decimal(18,2)),
    timestamp    Float64
) ENGINE = Kafka
SETTINGS kafka_broker_list = 'kafka:9092',
         kafka_topic_list = 'marketing.backend_events',
         kafka_group_name = 'ch_backend_events',
         kafka_format = 'JSONEachRow',
         kafka_num_consumers = 1;

CREATE TABLE IF NOT EXISTS marketing.raw_backend_events (
    event_id     String,
    user_id      UInt64,
    event_type   LowCardinality(String),
    order_id     Nullable(String),
    product_id   Nullable(UInt32),
    amount       Nullable(Decimal(18,2)),
    timestamp    DateTime64(3),
    event_date   Date DEFAULT toDate(timestamp)
) ENGINE = MergeTree()
PARTITION BY toYYYYMM(event_date)
ORDER BY (event_type, user_id, timestamp)
TTL event_date + INTERVAL 6 MONTH;

CREATE MATERIALIZED VIEW IF NOT EXISTS marketing.mv_kafka_backend_events TO marketing.raw_backend_events AS
SELECT
    src.event_id,
    src.user_id,
    src.event_type,
    src.order_id,
    src.product_id,
    src.amount,
    fromUnixTimestamp64Milli(toInt64(src.timestamp * 1000)) AS timestamp,
    toDate(fromUnixTimestamp64Milli(toInt64(src.timestamp * 1000))) AS event_date
FROM marketing.kafka_backend_events AS src;
