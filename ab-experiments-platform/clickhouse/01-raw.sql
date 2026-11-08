CREATE DATABASE IF NOT EXISTS ab;

-- Debezium CDC envelope: $.payload.after.* ; timestamptz as ZonedTimestamp string

CREATE TABLE IF NOT EXISTS ab.kafka_assignments (
    raw_message String
) ENGINE = Kafka
SETTINGS
    kafka_broker_list = 'kafka:9092',
    kafka_topic_list = 'ab.public.assignments',
    kafka_group_name = 'ch_ab_assignments_v2',
    kafka_format = 'JSONAsString',
    kafka_num_consumers = 1;

CREATE TABLE IF NOT EXISTS ab.raw_assignments (
    assignment_id  UInt64,
    experiment_id  UInt32,
    experiment_key LowCardinality(String),
    user_id        String,
    variant_key    LowCardinality(String),
    bucket         UInt16,
    assigned_at    DateTime64(3)
) ENGINE = ReplacingMergeTree(assigned_at)
PARTITION BY toYYYYMM(assigned_at)
ORDER BY (experiment_id, user_id)
TTL toDateTime(assigned_at) + INTERVAL 6 MONTH;

CREATE MATERIALIZED VIEW IF NOT EXISTS ab.mv_kafka_assignments TO ab.raw_assignments AS
SELECT
    toUInt64OrZero(JSON_VALUE(raw_message, '$.payload.after.assignment_id')) AS assignment_id,
    toUInt32OrZero(JSON_VALUE(raw_message, '$.payload.after.experiment_id')) AS experiment_id,
    JSON_VALUE(raw_message, '$.payload.after.experiment_key') AS experiment_key,
    JSON_VALUE(raw_message, '$.payload.after.user_id') AS user_id,
    JSON_VALUE(raw_message, '$.payload.after.variant_key') AS variant_key,
    toUInt16OrZero(JSON_VALUE(raw_message, '$.payload.after.bucket')) AS bucket,
    parseDateTime64BestEffortOrZero(
        replaceAll(JSON_VALUE(raw_message, '$.payload.after.assigned_at'), 'Z', '')
    ) AS assigned_at
FROM ab.kafka_assignments
WHERE JSON_VALUE(raw_message, '$.payload.op') IN ('c', 'r', 'u')
  AND JSON_VALUE(raw_message, '$.payload.after.assignment_id') != '';

CREATE TABLE IF NOT EXISTS ab.kafka_outcomes (
    raw_message String
) ENGINE = Kafka
SETTINGS
    kafka_broker_list = 'kafka:9092',
    kafka_topic_list = 'ab.public.outcomes',
    kafka_group_name = 'ch_ab_outcomes_v2',
    kafka_format = 'JSONAsString',
    kafka_num_consumers = 1;

CREATE TABLE IF NOT EXISTS ab.raw_outcomes (
    event_id       String,
    experiment_id  UInt32,
    experiment_key LowCardinality(String),
    user_id        String,
    event_type     LowCardinality(String),
    value          Float64,
    event_time     DateTime64(3)
) ENGINE = ReplacingMergeTree(event_time)
PARTITION BY toYYYYMM(event_time)
ORDER BY (experiment_id, user_id, event_id)
TTL toDateTime(event_time) + INTERVAL 6 MONTH;

CREATE MATERIALIZED VIEW IF NOT EXISTS ab.mv_kafka_outcomes TO ab.raw_outcomes AS
SELECT
    JSON_VALUE(raw_message, '$.payload.after.event_id') AS event_id,
    toUInt32OrZero(JSON_VALUE(raw_message, '$.payload.after.experiment_id')) AS experiment_id,
    JSON_VALUE(raw_message, '$.payload.after.experiment_key') AS experiment_key,
    JSON_VALUE(raw_message, '$.payload.after.user_id') AS user_id,
    JSON_VALUE(raw_message, '$.payload.after.event_type') AS event_type,
    toFloat64OrZero(JSON_VALUE(raw_message, '$.payload.after.value')) AS value,
    parseDateTime64BestEffortOrZero(
        replaceAll(JSON_VALUE(raw_message, '$.payload.after.event_time'), 'Z', '')
    ) AS event_time
FROM ab.kafka_outcomes
WHERE JSON_VALUE(raw_message, '$.payload.op') IN ('c', 'r', 'u')
  AND JSON_VALUE(raw_message, '$.payload.after.event_id') != '';
