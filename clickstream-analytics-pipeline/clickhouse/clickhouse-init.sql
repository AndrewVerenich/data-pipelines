-- =============================================================================
-- E-commerce Clickstream Analytics: ClickHouse schema
--
-- Pattern per topic:  Kafka engine table  ->  Materialized view  ->  MergeTree table.
-- All Flink sinks emit JSONEachRow records; columns below mirror the Kotlin models
-- in flink-job/src/main/kotlin/com/example/flink/model/.
-- =============================================================================

-- -----------------------------------------------------------------------------
-- 1) Dead-letter queue (invalid or unparseable events from Flink)
-- -----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS kafka_dead_letter (
    raw String,
    reason String,
    timestamp UInt64
) ENGINE = Kafka
SETTINGS kafka_broker_list = 'kafka:9092',
         kafka_topic_list = 'dead_letter',
         kafka_group_name = 'ch_dead_letter',
         kafka_format = 'JSONEachRow',
         kafka_num_consumers = 1,
         kafka_skip_broken_messages = 100;

CREATE TABLE IF NOT EXISTS dead_letter (
    raw String,
    reason LowCardinality(String),
    timestamp UInt64,
    event_time DateTime MATERIALIZED toDateTime(timestamp / 1000)
) ENGINE = MergeTree()
ORDER BY (event_time, reason);

CREATE MATERIALIZED VIEW IF NOT EXISTS dead_letter_mv TO dead_letter AS
SELECT raw, reason, timestamp FROM kafka_dead_letter;

-- -----------------------------------------------------------------------------
-- 2) Session events (session_start / session_end from SessionTracker)
-- -----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS kafka_session_events (
    sessionId String,
    userId String,
    eventType String,
    startTime UInt64,
    endTime UInt64,
    eventCount UInt32,
    pages Array(String),
    segment String,
    durationMs UInt64
) ENGINE = Kafka
SETTINGS kafka_broker_list = 'kafka:9092',
         kafka_topic_list = 'session_events',
         kafka_group_name = 'ch_session_events',
         kafka_format = 'JSONEachRow',
         kafka_num_consumers = 1,
         kafka_skip_broken_messages = 100;

CREATE TABLE IF NOT EXISTS session_events (
    sessionId String,
    userId String,
    eventType LowCardinality(String),
    startTime UInt64,
    endTime UInt64,
    eventCount UInt32,
    pages Array(String),
    segment LowCardinality(String),
    durationMs UInt64,
    start_ts DateTime MATERIALIZED toDateTime(startTime / 1000),
    end_ts DateTime MATERIALIZED toDateTime(endTime / 1000)
) ENGINE = MergeTree()
ORDER BY (userId, startTime);

CREATE MATERIALIZED VIEW IF NOT EXISTS session_events_mv TO session_events AS
SELECT sessionId, userId, eventType, startTime, endTime, eventCount, pages, segment, durationMs
FROM kafka_session_events;

-- -----------------------------------------------------------------------------
-- 3) Fraud alerts (side output from ClickFraudDetector)
-- -----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS kafka_fraud_alerts (
    userId String,
    ruleId String,
    ruleType String,
    eventType String,
    eventCount UInt32,
    windowStart UInt64,
    windowEnd UInt64,
    timestamp UInt64,
    segment String
) ENGINE = Kafka
SETTINGS kafka_broker_list = 'kafka:9092',
         kafka_topic_list = 'fraud_alerts',
         kafka_group_name = 'ch_fraud_alerts',
         kafka_format = 'JSONEachRow',
         kafka_num_consumers = 1,
         kafka_skip_broken_messages = 100;

CREATE TABLE IF NOT EXISTS fraud_alerts (
    userId String,
    ruleId LowCardinality(String),
    ruleType LowCardinality(String),
    eventType LowCardinality(String),
    eventCount UInt32,
    windowStart UInt64,
    windowEnd UInt64,
    timestamp UInt64,
    segment LowCardinality(String),
    event_time DateTime MATERIALIZED toDateTime(timestamp / 1000)
) ENGINE = MergeTree()
ORDER BY (event_time, userId);

CREATE MATERIALIZED VIEW IF NOT EXISTS fraud_alerts_mv TO fraud_alerts AS
SELECT userId, ruleId, ruleType, eventType, eventCount, windowStart, windowEnd, timestamp, segment
FROM kafka_fraud_alerts;

-- -----------------------------------------------------------------------------
-- 4) Funnel events (step transitions, ABANDONED, COMPLETED)
-- -----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS kafka_funnel_events (
    userId String,
    step String,
    previousStep Nullable(String),
    stepTimestamp UInt64,
    funnelStartTime UInt64,
    elapsedMs UInt64,
    segment String
) ENGINE = Kafka
SETTINGS kafka_broker_list = 'kafka:9092',
         kafka_topic_list = 'funnel_events',
         kafka_group_name = 'ch_funnel_events',
         kafka_format = 'JSONEachRow',
         kafka_num_consumers = 1,
         kafka_skip_broken_messages = 100;

CREATE TABLE IF NOT EXISTS funnel_events (
    userId String,
    step LowCardinality(String),
    previousStep LowCardinality(Nullable(String)),
    stepTimestamp UInt64,
    funnelStartTime UInt64,
    elapsedMs UInt64,
    segment LowCardinality(String),
    step_ts DateTime MATERIALIZED toDateTime(stepTimestamp / 1000)
) ENGINE = MergeTree()
ORDER BY (userId, stepTimestamp);

CREATE MATERIALIZED VIEW IF NOT EXISTS funnel_events_mv TO funnel_events AS
SELECT userId, step, previousStep, stepTimestamp, funnelStartTime, elapsedMs, segment
FROM kafka_funnel_events;

-- -----------------------------------------------------------------------------
-- 5) Events per type (tumbling 1 min)
-- -----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS kafka_events_per_type (
    metric String,
    key String,
    windowStart UInt64,
    windowEnd UInt64,
    value Float64
) ENGINE = Kafka
SETTINGS kafka_broker_list = 'kafka:9092',
         kafka_topic_list = 'events_per_type',
         kafka_group_name = 'ch_events_per_type',
         kafka_format = 'JSONEachRow',
         kafka_num_consumers = 1,
         kafka_skip_broken_messages = 100;

CREATE TABLE IF NOT EXISTS events_per_type (
    metric LowCardinality(String),
    key LowCardinality(String),
    windowStart UInt64,
    windowEnd UInt64,
    value Float64,
    window_ts DateTime MATERIALIZED toDateTime(windowStart / 1000)
) ENGINE = MergeTree()
ORDER BY (metric, key, windowStart);

CREATE MATERIALIZED VIEW IF NOT EXISTS events_per_type_mv TO events_per_type AS
SELECT metric, key, windowStart, windowEnd, value FROM kafka_events_per_type;

-- -----------------------------------------------------------------------------
-- 6) Page views (sliding 5m / 1m)
-- -----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS kafka_page_views (
    metric String,
    key String,
    windowStart UInt64,
    windowEnd UInt64,
    value Float64
) ENGINE = Kafka
SETTINGS kafka_broker_list = 'kafka:9092',
         kafka_topic_list = 'page_views',
         kafka_group_name = 'ch_page_views',
         kafka_format = 'JSONEachRow',
         kafka_num_consumers = 1,
         kafka_skip_broken_messages = 100;

CREATE TABLE IF NOT EXISTS page_views (
    metric LowCardinality(String),
    key LowCardinality(String),
    windowStart UInt64,
    windowEnd UInt64,
    value Float64,
    window_ts DateTime MATERIALIZED toDateTime(windowStart / 1000)
) ENGINE = MergeTree()
ORDER BY (metric, key, windowStart);

CREATE MATERIALIZED VIEW IF NOT EXISTS page_views_mv TO page_views AS
SELECT metric, key, windowStart, windowEnd, value FROM kafka_page_views;

-- -----------------------------------------------------------------------------
-- 7) Unique users per page (tumbling 1m)
-- -----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS kafka_unique_users_per_page (
    metric String,
    key String,
    windowStart UInt64,
    windowEnd UInt64,
    value Float64
) ENGINE = Kafka
SETTINGS kafka_broker_list = 'kafka:9092',
         kafka_topic_list = 'unique_users_per_page',
         kafka_group_name = 'ch_unique_users',
         kafka_format = 'JSONEachRow',
         kafka_num_consumers = 1,
         kafka_skip_broken_messages = 100;

CREATE TABLE IF NOT EXISTS unique_users_per_page (
    metric LowCardinality(String),
    key LowCardinality(String),
    windowStart UInt64,
    windowEnd UInt64,
    value Float64,
    window_ts DateTime MATERIALIZED toDateTime(windowStart / 1000)
) ENGINE = MergeTree()
ORDER BY (metric, key, windowStart);

CREATE MATERIALIZED VIEW IF NOT EXISTS unique_users_per_page_mv TO unique_users_per_page AS
SELECT metric, key, windowStart, windowEnd, value FROM kafka_unique_users_per_page;

-- -----------------------------------------------------------------------------
-- 8) Activity heatmap (hour-of-day, tumbling 1m)
-- -----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS kafka_activity_heatmap (
    metric String,
    key String,
    windowStart UInt64,
    windowEnd UInt64,
    value Float64
) ENGINE = Kafka
SETTINGS kafka_broker_list = 'kafka:9092',
         kafka_topic_list = 'activity_heatmap',
         kafka_group_name = 'ch_activity_heatmap',
         kafka_format = 'JSONEachRow',
         kafka_num_consumers = 1,
         kafka_skip_broken_messages = 100;

CREATE TABLE IF NOT EXISTS activity_heatmap (
    metric LowCardinality(String),
    key LowCardinality(String),
    windowStart UInt64,
    windowEnd UInt64,
    value Float64,
    window_ts DateTime MATERIALIZED toDateTime(windowStart / 1000)
) ENGINE = MergeTree()
ORDER BY (metric, key, windowStart);

CREATE MATERIALIZED VIEW IF NOT EXISTS activity_heatmap_mv TO activity_heatmap AS
SELECT metric, key, windowStart, windowEnd, value FROM kafka_activity_heatmap;
