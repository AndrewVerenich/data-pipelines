-- Payment Fraud Scoring Platform — ClickHouse landing via Kafka Engine + MV

CREATE DATABASE IF NOT EXISTS fraud;

-- =============================================================================
-- Kafka Engine (raw)
-- =============================================================================

CREATE TABLE fraud.raw_transactions_kafka
(
    value String
)
ENGINE = Kafka
SETTINGS
    kafka_broker_list = 'kafka:9092',
    kafka_topic_list = 'payments.transactions',
    kafka_group_name = 'ch_fraud_transactions',
    kafka_format = 'JSONAsString',
    kafka_num_consumers = 1;

CREATE TABLE fraud.raw_labels_kafka
(
    value String
)
ENGINE = Kafka
SETTINGS
    kafka_broker_list = 'kafka:9092',
    kafka_topic_list = 'payments.labels',
    kafka_group_name = 'ch_fraud_labels',
    kafka_format = 'JSONAsString',
    kafka_num_consumers = 1;

CREATE TABLE fraud.raw_scores_kafka
(
    value String
)
ENGINE = Kafka
SETTINGS
    kafka_broker_list = 'kafka:9092',
    kafka_topic_list = 'payments.scores',
    kafka_group_name = 'ch_fraud_scores',
    kafka_format = 'JSONAsString',
    kafka_num_consumers = 1;

CREATE TABLE fraud.raw_users_kafka
(
    value String
)
ENGINE = Kafka
SETTINGS
    kafka_broker_list = 'kafka:9092',
    kafka_topic_list = 'payments.users',
    kafka_group_name = 'ch_fraud_users',
    kafka_format = 'JSONAsString',
    kafka_num_consumers = 1;

CREATE TABLE fraud.raw_merchants_kafka
(
    value String
)
ENGINE = Kafka
SETTINGS
    kafka_broker_list = 'kafka:9092',
    kafka_topic_list = 'payments.merchants',
    kafka_group_name = 'ch_fraud_merchants',
    kafka_format = 'JSONAsString',
    kafka_num_consumers = 1;

-- =============================================================================
-- Landing MergeTree
-- =============================================================================

CREATE TABLE fraud.landing_transactions
(
    transaction_id String,
    event_ts DateTime64(3, 'UTC'),
    user_id String,
    merchant_id String,
    device_id String,
    payment_method_id String,
    amount Float64,
    currency LowCardinality(String),
    channel LowCardinality(String),
    status LowCardinality(String),
    ip_country LowCardinality(String),
    is_3ds UInt8,
    card_bin String,
    device_os LowCardinality(String),
    mcc LowCardinality(String),
    merchant_country LowCardinality(String),
    ingest_ts DateTime64(3, 'UTC') DEFAULT now64(3)
)
ENGINE = MergeTree
PARTITION BY toYYYYMM(event_ts)
ORDER BY (event_ts, user_id, transaction_id);

CREATE TABLE fraud.landing_labels
(
    transaction_id String,
    is_fraud UInt8,
    label_source LowCardinality(String),
    fraud_pattern LowCardinality(String),
    labeled_at DateTime64(3, 'UTC'),
    ingest_ts DateTime64(3, 'UTC') DEFAULT now64(3)
)
ENGINE = ReplacingMergeTree(labeled_at)
ORDER BY transaction_id;

CREATE TABLE fraud.landing_scores
(
    transaction_id String,
    score Float64,
    decision LowCardinality(String),
    model_version String,
    scored_at DateTime64(3, 'UTC'),
    top_features String,
    ingest_ts DateTime64(3, 'UTC') DEFAULT now64(3)
)
ENGINE = ReplacingMergeTree(scored_at)
ORDER BY (transaction_id, model_version);

CREATE TABLE fraud.landing_users
(
    user_id String,
    email String,
    country LowCardinality(String),
    kyc_level LowCardinality(String),
    risk_tier LowCardinality(String),
    created_at DateTime64(3, 'UTC'),
    updated_at DateTime64(3, 'UTC'),
    ingest_ts DateTime64(3, 'UTC') DEFAULT now64(3)
)
ENGINE = ReplacingMergeTree(updated_at)
ORDER BY user_id;

CREATE TABLE fraud.landing_merchants
(
    merchant_id String,
    merchant_name String,
    mcc LowCardinality(String),
    country LowCardinality(String),
    risk_score Float64,
    created_at DateTime64(3, 'UTC'),
    ingest_ts DateTime64(3, 'UTC') DEFAULT now64(3)
)
ENGINE = ReplacingMergeTree(created_at)
ORDER BY merchant_id;

-- =============================================================================
-- Materialized Views
-- =============================================================================

CREATE MATERIALIZED VIEW fraud.mv_landing_transactions TO fraud.landing_transactions AS
SELECT
    JSONExtractString(value, 'transaction_id') AS transaction_id,
    parseDateTime64BestEffort(JSONExtractString(value, 'event_ts'), 3, 'UTC') AS event_ts,
    JSONExtractString(value, 'user_id') AS user_id,
    JSONExtractString(value, 'merchant_id') AS merchant_id,
    JSONExtractString(value, 'device_id') AS device_id,
    JSONExtractString(value, 'payment_method_id') AS payment_method_id,
    JSONExtractFloat(value, 'amount') AS amount,
    JSONExtractString(value, 'currency') AS currency,
    JSONExtractString(value, 'channel') AS channel,
    JSONExtractString(value, 'status') AS status,
    JSONExtractString(value, 'ip_country') AS ip_country,
    toUInt8(JSONExtractUInt(value, 'is_3ds')) AS is_3ds,
    JSONExtractString(value, 'card_bin') AS card_bin,
    JSONExtractString(value, 'device_os') AS device_os,
    JSONExtractString(value, 'mcc') AS mcc,
    JSONExtractString(value, 'merchant_country') AS merchant_country,
    now64(3) AS ingest_ts
FROM fraud.raw_transactions_kafka;

CREATE MATERIALIZED VIEW fraud.mv_landing_labels TO fraud.landing_labels AS
SELECT
    JSONExtractString(value, 'transaction_id') AS transaction_id,
    toUInt8(JSONExtractUInt(value, 'is_fraud')) AS is_fraud,
    JSONExtractString(value, 'label_source') AS label_source,
    JSONExtractString(value, 'fraud_pattern') AS fraud_pattern,
    parseDateTime64BestEffort(JSONExtractString(value, 'labeled_at'), 3, 'UTC') AS labeled_at,
    now64(3) AS ingest_ts
FROM fraud.raw_labels_kafka;

CREATE MATERIALIZED VIEW fraud.mv_landing_scores TO fraud.landing_scores AS
SELECT
    JSONExtractString(value, 'transaction_id') AS transaction_id,
    JSONExtractFloat(value, 'score') AS score,
    JSONExtractString(value, 'decision') AS decision,
    JSONExtractString(value, 'model_version') AS model_version,
    parseDateTime64BestEffort(JSONExtractString(value, 'scored_at'), 3, 'UTC') AS scored_at,
    JSONExtractString(value, 'top_features') AS top_features,
    now64(3) AS ingest_ts
FROM fraud.raw_scores_kafka;

CREATE MATERIALIZED VIEW fraud.mv_landing_users TO fraud.landing_users AS
SELECT
    JSONExtractString(value, 'user_id') AS user_id,
    JSONExtractString(value, 'email') AS email,
    JSONExtractString(value, 'country') AS country,
    JSONExtractString(value, 'kyc_level') AS kyc_level,
    JSONExtractString(value, 'risk_tier') AS risk_tier,
    parseDateTime64BestEffort(JSONExtractString(value, 'created_at'), 3, 'UTC') AS created_at,
    parseDateTime64BestEffort(JSONExtractString(value, 'updated_at'), 3, 'UTC') AS updated_at,
    now64(3) AS ingest_ts
FROM fraud.raw_users_kafka;

CREATE MATERIALIZED VIEW fraud.mv_landing_merchants TO fraud.landing_merchants AS
SELECT
    JSONExtractString(value, 'merchant_id') AS merchant_id,
    JSONExtractString(value, 'merchant_name') AS merchant_name,
    JSONExtractString(value, 'mcc') AS mcc,
    JSONExtractString(value, 'country') AS country,
    JSONExtractFloat(value, 'risk_score') AS risk_score,
    parseDateTime64BestEffort(JSONExtractString(value, 'created_at'), 3, 'UTC') AS created_at,
    now64(3) AS ingest_ts
FROM fraud.raw_merchants_kafka;
