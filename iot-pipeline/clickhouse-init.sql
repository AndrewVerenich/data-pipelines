-- Inbound Kafka -> MergeTree for Grafana. JSON keys use camelCase (e.g. roomId) from Spring JsonSerializer.

CREATE TABLE IF NOT EXISTS sensor_temperature_kafka (
    roomId String,
    temperature Float64,
    ts String
) ENGINE = Kafka
SETTINGS kafka_broker_list = 'kafka:9092',
         kafka_topic_list = 'sensor.temperature',
         kafka_group_name = 'clickhouse-smart-temp',
         kafka_format = 'JSONEachRow',
         kafka_num_consumers = 1;

CREATE TABLE IF NOT EXISTS sensor_temperature (
    room_id String,
    temperature Float64,
    event_time DateTime
) ENGINE = MergeTree()
ORDER BY (room_id, event_time);

CREATE MATERIALIZED VIEW IF NOT EXISTS sensor_temperature_mv TO sensor_temperature AS
SELECT
    roomId AS room_id,
    temperature,
    parseDateTimeBestEffortOrNull(ts) AS event_time
FROM sensor_temperature_kafka;

CREATE TABLE IF NOT EXISTS sensor_humidity_kafka (
    roomId String,
    humidity Float64,
    ts String
) ENGINE = Kafka
SETTINGS kafka_broker_list = 'kafka:9092',
         kafka_topic_list = 'sensor.humidity',
         kafka_group_name = 'clickhouse-smart-hum',
         kafka_format = 'JSONEachRow',
         kafka_num_consumers = 1;

CREATE TABLE IF NOT EXISTS sensor_humidity (
    room_id String,
    humidity Float64,
    event_time DateTime
) ENGINE = MergeTree()
ORDER BY (room_id, event_time);

CREATE MATERIALIZED VIEW IF NOT EXISTS sensor_humidity_mv TO sensor_humidity AS
SELECT roomId AS room_id, humidity, parseDateTimeBestEffortOrNull(ts) AS event_time
FROM sensor_humidity_kafka;

CREATE TABLE IF NOT EXISTS commands_hvac_kafka (
    roomId String,
    action String,
    reason String,
    ts String
) ENGINE = Kafka
SETTINGS kafka_broker_list = 'kafka:9092',
         kafka_topic_list = 'command.hvac',
         kafka_group_name = 'clickhouse-hvac-cmd',
         kafka_format = 'JSONEachRow',
         kafka_num_consumers = 1;

CREATE TABLE IF NOT EXISTS commands_hvac (
    room_id String,
    action String,
    reason String,
    event_time DateTime
) ENGINE = MergeTree()
ORDER BY (room_id, event_time);

CREATE MATERIALIZED VIEW IF NOT EXISTS commands_hvac_mv TO commands_hvac AS
SELECT
    roomId AS room_id,
    action,
    reason,
    parseDateTimeBestEffortOrNull(ts) AS event_time
FROM commands_hvac_kafka;

CREATE TABLE IF NOT EXISTS commands_lighting_kafka (
    roomId String,
    action String,
    reason String,
    ts String
) ENGINE = Kafka
SETTINGS kafka_broker_list = 'kafka:9092',
         kafka_topic_list = 'command.lighting',
         kafka_group_name = 'clickhouse-lighting-cmd',
         kafka_format = 'JSONEachRow',
         kafka_num_consumers = 1;

CREATE TABLE IF NOT EXISTS commands_lighting (
    room_id String,
    action String,
    reason String,
    event_time DateTime
) ENGINE = MergeTree()
ORDER BY (room_id, event_time);

CREATE MATERIALIZED VIEW IF NOT EXISTS commands_lighting_mv TO commands_lighting AS
SELECT
    roomId AS room_id,
    action,
    reason,
    parseDateTimeBestEffortOrNull(ts) AS event_time
FROM commands_lighting_kafka;

CREATE TABLE IF NOT EXISTS analytics_climate_kafka (
    roomId String,
    avg_temp Float64,
    desired_temperature Float64,
    ts String
) ENGINE = Kafka
SETTINGS kafka_broker_list = 'kafka:9092',
         kafka_topic_list = 'analytics.climate',
         kafka_group_name = 'clickhouse-analytics-climate',
         kafka_format = 'JSONEachRow',
         kafka_num_consumers = 1;

CREATE TABLE IF NOT EXISTS analytics_climate (
    room_id String,
    avg_temp Float64,
    desired_temperature Float64,
    event_time DateTime
) ENGINE = MergeTree()
ORDER BY (room_id, event_time);

CREATE MATERIALIZED VIEW IF NOT EXISTS analytics_climate_mv TO analytics_climate AS
SELECT
    roomId AS room_id,
    avg_temp,
    desired_temperature,
    parseDateTimeBestEffortOrNull(ts) AS event_time
FROM analytics_climate_kafka;

CREATE TABLE IF NOT EXISTS alerts_security_kafka (
    roomId String,
    type String,
    severity String,
    detail String,
    ts String
) ENGINE = Kafka
SETTINGS kafka_broker_list = 'kafka:9092',
         kafka_topic_list = 'alert.security',
         kafka_group_name = 'clickhouse-alerts-sec',
         kafka_format = 'JSONEachRow',
         kafka_num_consumers = 1;

CREATE TABLE IF NOT EXISTS alerts_security (
    room_id String,
    type String,
    severity String,
    detail String,
    event_time DateTime
) ENGINE = MergeTree()
ORDER BY (event_time, room_id);

CREATE MATERIALIZED VIEW IF NOT EXISTS alerts_security_mv TO alerts_security AS
SELECT
    roomId AS room_id,
    type,
    severity,
    detail,
    parseDateTimeBestEffortOrNull(ts) AS event_time
FROM alerts_security_kafka;
