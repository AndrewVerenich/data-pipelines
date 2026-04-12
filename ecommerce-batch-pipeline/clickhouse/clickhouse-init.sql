CREATE DATABASE IF NOT EXISTS ecommerce_dwh;

CREATE TABLE IF NOT EXISTS ecommerce_dwh.raw_ecommerce_events
(
    ingest_batch_id String,
    event_date Date,
    event_ts DateTime64(3),
    minute String,
    level LowCardinality(String),
    event LowCardinality(String),
    user_id String,
    session_id String,
    device LowCardinality(String),
    page LowCardinality(String),
    error_type Nullable(String),
    payment_method Nullable(String),
    category Nullable(String),
    product_id Nullable(String),
    order_id Nullable(String),
    user_country LowCardinality(String),
    user_email Nullable(String),
    product_name Nullable(String),
    product_category_ref Nullable(String),
    unit_price Nullable(Float64)
)
ENGINE = MergeTree()
PARTITION BY ingest_batch_id
ORDER BY (event_date, event_ts, user_id, session_id, event);

CREATE TABLE IF NOT EXISTS ecommerce_dwh.raw_ref_users
(
    ingest_batch_id String,
    user_id String,
    email Nullable(String),
    country LowCardinality(String),
    cohort LowCardinality(String)
)
ENGINE = MergeTree()
PARTITION BY ingest_batch_id
ORDER BY (user_id, ingest_batch_id);

CREATE TABLE IF NOT EXISTS ecommerce_dwh.raw_ref_products
(
    ingest_batch_id String,
    product_id String,
    product_name String,
    category LowCardinality(String),
    unit_price Float64
)
ENGINE = MergeTree()
PARTITION BY ingest_batch_id
ORDER BY (product_id, ingest_batch_id);
