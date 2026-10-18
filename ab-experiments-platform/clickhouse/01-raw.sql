CREATE DATABASE IF NOT EXISTS ab;

CREATE TABLE IF NOT EXISTS ab.kafka_exposures (
    exposure_id      String,
    experiment_id    UInt32,
    experiment_key   String,
    variant_key      String,
    user_id          String,
    bucket           UInt16,
    exposed_at       Float64,
    exposure_version UInt64
) ENGINE = Kafka
SETTINGS
    kafka_broker_list = 'kafka:9092',
    kafka_topic_list = 'ab.exposures',
    kafka_group_name = 'ch_ab_exposures',
    kafka_format = 'JSONEachRow',
    kafka_num_consumers = 1;

CREATE TABLE IF NOT EXISTS ab.raw_exposures (
    exposure_id      String,
    experiment_id    UInt32,
    experiment_key   LowCardinality(String),
    variant_key      LowCardinality(String),
    user_id          String,
    bucket           UInt16,
    exposed_at       DateTime64(3),
    exposure_version UInt64
) ENGINE = ReplacingMergeTree(exposure_version)
PARTITION BY toYYYYMM(exposed_at)
ORDER BY (experiment_id, user_id)
TTL toDateTime(exposed_at) + INTERVAL 6 MONTH;

CREATE MATERIALIZED VIEW IF NOT EXISTS ab.mv_kafka_exposures TO ab.raw_exposures AS
SELECT
    exposure_id,
    experiment_id,
    experiment_key,
    variant_key,
    user_id,
    bucket,
    fromUnixTimestamp64Milli(toInt64(exposed_at * 1000)) AS exposed_at,
    exposure_version
FROM ab.kafka_exposures;

CREATE TABLE IF NOT EXISTS ab.kafka_outcomes (
    event_id    String,
    user_id     String,
    event_type  String,
    value       Float64,
    event_time  Float64
) ENGINE = Kafka
SETTINGS
    kafka_broker_list = 'kafka:9092',
    kafka_topic_list = 'ab.outcomes',
    kafka_group_name = 'ch_ab_outcomes',
    kafka_format = 'JSONEachRow',
    kafka_num_consumers = 1;

CREATE TABLE IF NOT EXISTS ab.raw_outcomes (
    event_id   String,
    user_id    String,
    event_type LowCardinality(String),
    value      Float64,
    event_time DateTime64(3)
) ENGINE = MergeTree
PARTITION BY toYYYYMM(event_time)
ORDER BY (user_id, event_time, event_id)
TTL toDateTime(event_time) + INTERVAL 6 MONTH;

CREATE MATERIALIZED VIEW IF NOT EXISTS ab.mv_kafka_outcomes TO ab.raw_outcomes AS
SELECT
    event_id,
    user_id,
    event_type,
    value,
    fromUnixTimestamp64Milli(toInt64(event_time * 1000)) AS event_time
FROM ab.kafka_outcomes;
