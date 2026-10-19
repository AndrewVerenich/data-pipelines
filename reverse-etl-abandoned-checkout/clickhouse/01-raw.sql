CREATE DATABASE IF NOT EXISTS retl;

CREATE TABLE IF NOT EXISTS retl.kafka_commerce_events (
    event_id   String,
    user_id    String,
    event_type String,
    email      String,
    product_id String,
    cart_value Float64,
    session_id String,
    ts         Float64
) ENGINE = Kafka
SETTINGS
    kafka_broker_list = 'kafka:9092',
    kafka_topic_list = 'commerce.events',
    kafka_group_name = 'ch_retl_commerce',
    kafka_format = 'JSONEachRow',
    kafka_num_consumers = 1;

CREATE TABLE IF NOT EXISTS retl.raw_commerce_events (
    event_id   String,
    user_id    String,
    event_type LowCardinality(String),
    email      String,
    product_id LowCardinality(String),
    cart_value Float64,
    session_id String,
    event_time DateTime64(3)
) ENGINE = MergeTree
PARTITION BY toYYYYMMDD(event_time)
ORDER BY (user_id, event_time, event_id)
TTL toDateTime(event_time) + INTERVAL 30 DAY;

CREATE MATERIALIZED VIEW IF NOT EXISTS retl.mv_kafka_commerce_events TO retl.raw_commerce_events AS
SELECT
    event_id,
    user_id,
    event_type,
    email,
    product_id,
    cart_value,
    session_id,
    fromUnixTimestamp64Milli(toInt64(ts * 1000)) AS event_time
FROM retl.kafka_commerce_events;

-- Placeholder so sync-worker can start before the first dbt run.
CREATE TABLE IF NOT EXISTS retl.audience_abandoned_checkout (
    user_id     String,
    email       String,
    cart_value  Float64,
    version     String,
    checkout_at DateTime64(3),
    computed_at DateTime64(3) DEFAULT now64(3)
) ENGINE = MergeTree
ORDER BY user_id;
