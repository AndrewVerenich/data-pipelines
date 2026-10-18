CREATE DATABASE IF NOT EXISTS logistics;

CREATE TABLE IF NOT EXISTS logistics.kafka_gps_pings (
    ping_id      String,
    trip_id      String,
    courier_id   String,
    warehouse_id String,
    lat          Float64,
    lon          Float64,
    speed_kmh    Float64,
    ts           Float64
) ENGINE = Kafka
SETTINGS
    kafka_broker_list = 'kafka:9092',
    kafka_topic_list = 'logistics.gps_pings',
    kafka_group_name = 'ch_logistics_gps',
    kafka_format = 'JSONEachRow',
    kafka_num_consumers = 1;

CREATE TABLE IF NOT EXISTS logistics.raw_gps_pings (
    ping_id      String,
    trip_id      String,
    courier_id   LowCardinality(String),
    warehouse_id LowCardinality(String),
    lat          Float64,
    lon          Float64,
    speed_kmh    Float64,
    event_time   DateTime64(3)
) ENGINE = MergeTree
PARTITION BY toYYYYMMDD(event_time)
ORDER BY (trip_id, event_time, ping_id)
TTL toDateTime(event_time) + INTERVAL 30 DAY;

CREATE MATERIALIZED VIEW IF NOT EXISTS logistics.mv_kafka_gps_pings TO logistics.raw_gps_pings AS
SELECT
    ping_id,
    trip_id,
    courier_id,
    warehouse_id,
    lat,
    lon,
    speed_kmh,
    fromUnixTimestamp64Milli(toInt64(ts * 1000)) AS event_time
FROM logistics.kafka_gps_pings;

CREATE TABLE IF NOT EXISTS logistics.kafka_trip_events (
    event_id     String,
    trip_id      String,
    courier_id   String,
    warehouse_id String,
    event_type   String,
    promised_eta Float64,
    lat          Float64,
    lon          Float64,
    ts           Float64
) ENGINE = Kafka
SETTINGS
    kafka_broker_list = 'kafka:9092',
    kafka_topic_list = 'logistics.trip_events',
    kafka_group_name = 'ch_logistics_events',
    kafka_format = 'JSONEachRow',
    kafka_num_consumers = 1;

CREATE TABLE IF NOT EXISTS logistics.raw_trip_events (
    event_id     String,
    trip_id      String,
    courier_id   LowCardinality(String),
    warehouse_id LowCardinality(String),
    event_type   LowCardinality(String),
    promised_eta DateTime64(3),
    lat          Float64,
    lon          Float64,
    event_time   DateTime64(3)
) ENGINE = MergeTree
PARTITION BY toYYYYMMDD(event_time)
ORDER BY (trip_id, event_time, event_id)
TTL toDateTime(event_time) + INTERVAL 30 DAY;

CREATE MATERIALIZED VIEW IF NOT EXISTS logistics.mv_kafka_trip_events TO logistics.raw_trip_events AS
SELECT
    event_id,
    trip_id,
    courier_id,
    warehouse_id,
    event_type,
    fromUnixTimestamp64Milli(toInt64(promised_eta * 1000)) AS promised_eta,
    lat,
    lon,
    fromUnixTimestamp64Milli(toInt64(ts * 1000)) AS event_time
FROM logistics.kafka_trip_events;
