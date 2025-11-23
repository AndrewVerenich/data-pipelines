CREATE TABLE raw_transactions
(
    value String
)
    ENGINE = Kafka
SETTINGS kafka_broker_list = 'kafka:9092',
         kafka_topic_list = 'fintech.public.transactions',
         kafka_group_name = 'clickhouse_group',
         kafka_format = 'JSONAsString',
         kafka_num_consumers = 1;

CREATE TABLE transactions
(
    id         UInt64,
    user_id    UInt64,
    amount     Float64,
    status     LowCardinality(String),
    created_at DateTime64(6)
) ENGINE = MergeTree()
ORDER BY id;

CREATE MATERIALIZED VIEW mv_transactions TO transactions AS
SELECT
    toUInt64(JSON_VALUE(value, '$.payload.after.id'))        AS id,
    toUInt64(JSON_VALUE(value, '$.payload.after.user_id'))   AS user_id,
    toFloat64(JSON_VALUE(value, '$.payload.after.amount'))   AS amount,
    JSON_VALUE(value, '$.payload.after.status')              AS status,
    toDateTime64(
            toInt64(JSON_VALUE(value, '$.payload.after.created_at')) / 1000000,
            6
    )                                                       AS created_at
FROM raw_transactions
WHERE JSON_VALUE(value, '$.payload.after') IS NOT NULL;


