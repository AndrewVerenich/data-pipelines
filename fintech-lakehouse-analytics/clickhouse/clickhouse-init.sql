CREATE TABLE raw_customers (value String)
ENGINE = Kafka
SETTINGS kafka_broker_list = 'kafka:9092', kafka_topic_list = 'fintech.public.customers', kafka_group_name = 'clickhouse_group_customers', kafka_format = 'JSONAsString', kafka_num_consumers = 1;

CREATE TABLE raw_accounts (value String)
ENGINE = Kafka
SETTINGS kafka_broker_list = 'kafka:9092', kafka_topic_list = 'fintech.public.accounts', kafka_group_name = 'clickhouse_group_accounts', kafka_format = 'JSONAsString', kafka_num_consumers = 1;

CREATE TABLE raw_merchants (value String)
ENGINE = Kafka
SETTINGS kafka_broker_list = 'kafka:9092', kafka_topic_list = 'fintech.public.merchants', kafka_group_name = 'clickhouse_group_merchants', kafka_format = 'JSONAsString', kafka_num_consumers = 1;

CREATE TABLE raw_transactions (value String)
ENGINE = Kafka
SETTINGS kafka_broker_list = 'kafka:9092', kafka_topic_list = 'fintech.public.transactions', kafka_group_name = 'clickhouse_group_transactions', kafka_format = 'JSONAsString', kafka_num_consumers = 1;

CREATE TABLE raw_loans (value String)
ENGINE = Kafka
SETTINGS kafka_broker_list = 'kafka:9092', kafka_topic_list = 'fintech.public.loans', kafka_group_name = 'clickhouse_group_loans', kafka_format = 'JSONAsString', kafka_num_consumers = 1;

CREATE TABLE raw_loan_payments (value String)
ENGINE = Kafka
SETTINGS kafka_broker_list = 'kafka:9092', kafka_topic_list = 'fintech.public.loan_payments', kafka_group_name = 'clickhouse_group_loan_payments', kafka_format = 'JSONAsString', kafka_num_consumers = 1;

CREATE TABLE raw_exchange_rates (value String)
ENGINE = Kafka
SETTINGS kafka_broker_list = 'kafka:9092', kafka_topic_list = 'fintech.public.exchange_rates', kafka_group_name = 'clickhouse_group_exchange_rates', kafka_format = 'JSONAsString', kafka_num_consumers = 1;

CREATE TABLE customers
(
    id UInt64,
    full_name String,
    email String,
    phone Nullable(String),
    country LowCardinality(String),
    city LowCardinality(String),
    date_of_birth Date,
    kyc_status LowCardinality(String),
    risk_level LowCardinality(String),
    is_active UInt8,
    created_at DateTime64(6),
    updated_at DateTime64(6)
) ENGINE = MergeTree()
ORDER BY id;

CREATE TABLE accounts
(
    id UInt64,
    customer_id UInt64,
    account_type LowCardinality(String),
    account_status LowCardinality(String),
    currency_code LowCardinality(String),
    balance Float64,
    credit_limit Float64,
    interest_rate Float64,
    opened_at DateTime64(6),
    updated_at DateTime64(6)
) ENGINE = MergeTree()
ORDER BY (customer_id, id);

CREATE TABLE merchants
(
    id UInt64,
    merchant_name String,
    merchant_category LowCardinality(String),
    mcc_code String,
    country LowCardinality(String),
    city LowCardinality(String),
    is_online UInt8,
    created_at DateTime64(6)
) ENGINE = MergeTree()
ORDER BY (merchant_category, id);

CREATE TABLE transactions
(
    id UInt64,
    customer_id UInt64,
    account_id UInt64,
    merchant_id Nullable(UInt64),
    transaction_type LowCardinality(String),
    transaction_status LowCardinality(String),
    payment_channel LowCardinality(String),
    device_type LowCardinality(String),
    currency_code LowCardinality(String),
    exchange_rate Float64,
    amount Float64,
    amount_usd Float64,
    fee_amount Float64,
    is_international UInt8,
    created_at DateTime64(6),
    updated_at DateTime64(6)
) ENGINE = MergeTree()
ORDER BY (toDate(created_at), account_id, id);

CREATE TABLE loans
(
    id UInt64,
    customer_id UInt64,
    account_id UInt64,
    loan_type LowCardinality(String),
    principal_amount Float64,
    interest_rate Float64,
    term_months UInt32,
    monthly_payment Float64,
    outstanding_balance Float64,
    loan_status LowCardinality(String),
    issued_at DateTime64(6),
    maturity_at DateTime64(6),
    updated_at DateTime64(6)
) ENGINE = MergeTree()
ORDER BY (customer_id, id);

CREATE TABLE loan_payments
(
    id UInt64,
    loan_id UInt64,
    payment_amount Float64,
    principal_portion Float64,
    interest_portion Float64,
    payment_status LowCardinality(String),
    payment_channel LowCardinality(String),
    payment_date DateTime64(6),
    created_at DateTime64(6)
) ENGINE = MergeTree()
ORDER BY (loan_id, payment_date, id);

CREATE TABLE exchange_rates
(
    id UInt64,
    base_currency LowCardinality(String),
    target_currency LowCardinality(String),
    rate Float64,
    effective_date Date,
    created_at DateTime64(6)
) ENGINE = MergeTree()
ORDER BY (base_currency, target_currency, effective_date);

CREATE MATERIALIZED VIEW mv_customers TO customers AS
SELECT
    toUInt64(JSON_VALUE(value, '$.payload.after.id')) AS id,
    JSON_VALUE(value, '$.payload.after.full_name') AS full_name,
    JSON_VALUE(value, '$.payload.after.email') AS email,
    JSON_VALUE(value, '$.payload.after.phone') AS phone,
    JSON_VALUE(value, '$.payload.after.country') AS country,
    JSON_VALUE(value, '$.payload.after.city') AS city,
    addDays(toDate('1970-01-01'), toInt32(JSON_VALUE(value, '$.payload.after.date_of_birth'))) AS date_of_birth,
    JSON_VALUE(value, '$.payload.after.kyc_status') AS kyc_status,
    JSON_VALUE(value, '$.payload.after.risk_level') AS risk_level,
    toUInt8(lowerUTF8(JSON_VALUE(value, '$.payload.after.is_active')) = 'true') AS is_active,
    toDateTime64(toInt64(JSON_VALUE(value, '$.payload.after.created_at')) / 1000000, 6) AS created_at,
    toDateTime64(toInt64(JSON_VALUE(value, '$.payload.after.updated_at')) / 1000000, 6) AS updated_at
FROM raw_customers
WHERE JSON_VALUE(value, '$.payload.after') IS NOT NULL;

CREATE MATERIALIZED VIEW mv_accounts TO accounts AS
SELECT
    toUInt64(JSON_VALUE(value, '$.payload.after.id')) AS id,
    toUInt64(JSON_VALUE(value, '$.payload.after.customer_id')) AS customer_id,
    JSON_VALUE(value, '$.payload.after.account_type') AS account_type,
    JSON_VALUE(value, '$.payload.after.account_status') AS account_status,
    JSON_VALUE(value, '$.payload.after.currency_code') AS currency_code,
    toFloat64(JSON_VALUE(value, '$.payload.after.balance')) AS balance,
    toFloat64(JSON_VALUE(value, '$.payload.after.credit_limit')) AS credit_limit,
    toFloat64(JSON_VALUE(value, '$.payload.after.interest_rate')) AS interest_rate,
    toDateTime64(toInt64(JSON_VALUE(value, '$.payload.after.opened_at')) / 1000000, 6) AS opened_at,
    toDateTime64(toInt64(JSON_VALUE(value, '$.payload.after.updated_at')) / 1000000, 6) AS updated_at
FROM raw_accounts
WHERE JSON_VALUE(value, '$.payload.after') IS NOT NULL;

CREATE MATERIALIZED VIEW mv_merchants TO merchants AS
SELECT
    toUInt64(JSON_VALUE(value, '$.payload.after.id')) AS id,
    JSON_VALUE(value, '$.payload.after.merchant_name') AS merchant_name,
    JSON_VALUE(value, '$.payload.after.merchant_category') AS merchant_category,
    JSON_VALUE(value, '$.payload.after.mcc_code') AS mcc_code,
    JSON_VALUE(value, '$.payload.after.country') AS country,
    JSON_VALUE(value, '$.payload.after.city') AS city,
    toUInt8(lowerUTF8(JSON_VALUE(value, '$.payload.after.is_online')) = 'true') AS is_online,
    toDateTime64(toInt64(JSON_VALUE(value, '$.payload.after.created_at')) / 1000000, 6) AS created_at
FROM raw_merchants
WHERE JSON_VALUE(value, '$.payload.after') IS NOT NULL;

CREATE MATERIALIZED VIEW mv_transactions TO transactions AS
SELECT
    toUInt64(JSON_VALUE(value, '$.payload.after.id')) AS id,
    toUInt64(JSON_VALUE(value, '$.payload.after.customer_id')) AS customer_id,
    toUInt64(JSON_VALUE(value, '$.payload.after.account_id')) AS account_id,
    nullIf(toUInt64OrZero(JSON_VALUE(value, '$.payload.after.merchant_id')), 0) AS merchant_id,
    JSON_VALUE(value, '$.payload.after.transaction_type') AS transaction_type,
    JSON_VALUE(value, '$.payload.after.transaction_status') AS transaction_status,
    JSON_VALUE(value, '$.payload.after.payment_channel') AS payment_channel,
    JSON_VALUE(value, '$.payload.after.device_type') AS device_type,
    JSON_VALUE(value, '$.payload.after.currency_code') AS currency_code,
    toFloat64(JSON_VALUE(value, '$.payload.after.exchange_rate')) AS exchange_rate,
    toFloat64(JSON_VALUE(value, '$.payload.after.amount')) AS amount,
    toFloat64(JSON_VALUE(value, '$.payload.after.amount_usd')) AS amount_usd,
    toFloat64(JSON_VALUE(value, '$.payload.after.fee_amount')) AS fee_amount,
    toUInt8(lowerUTF8(JSON_VALUE(value, '$.payload.after.is_international')) = 'true') AS is_international,
    toDateTime64(toInt64(JSON_VALUE(value, '$.payload.after.created_at')) / 1000000, 6) AS created_at,
    toDateTime64(toInt64(JSON_VALUE(value, '$.payload.after.updated_at')) / 1000000, 6) AS updated_at
FROM raw_transactions
WHERE JSON_VALUE(value, '$.payload.after') IS NOT NULL;

CREATE MATERIALIZED VIEW mv_loans TO loans AS
SELECT
    toUInt64(JSON_VALUE(value, '$.payload.after.id')) AS id,
    toUInt64(JSON_VALUE(value, '$.payload.after.customer_id')) AS customer_id,
    toUInt64(JSON_VALUE(value, '$.payload.after.account_id')) AS account_id,
    JSON_VALUE(value, '$.payload.after.loan_type') AS loan_type,
    toFloat64(JSON_VALUE(value, '$.payload.after.principal_amount')) AS principal_amount,
    toFloat64(JSON_VALUE(value, '$.payload.after.interest_rate')) AS interest_rate,
    toUInt32(JSON_VALUE(value, '$.payload.after.term_months')) AS term_months,
    toFloat64(JSON_VALUE(value, '$.payload.after.monthly_payment')) AS monthly_payment,
    toFloat64(JSON_VALUE(value, '$.payload.after.outstanding_balance')) AS outstanding_balance,
    JSON_VALUE(value, '$.payload.after.loan_status') AS loan_status,
    toDateTime64(toInt64(JSON_VALUE(value, '$.payload.after.issued_at')) / 1000000, 6) AS issued_at,
    toDateTime64(toInt64(JSON_VALUE(value, '$.payload.after.maturity_at')) / 1000000, 6) AS maturity_at,
    toDateTime64(toInt64(JSON_VALUE(value, '$.payload.after.updated_at')) / 1000000, 6) AS updated_at
FROM raw_loans
WHERE JSON_VALUE(value, '$.payload.after') IS NOT NULL;

CREATE MATERIALIZED VIEW mv_loan_payments TO loan_payments AS
SELECT
    toUInt64(JSON_VALUE(value, '$.payload.after.id')) AS id,
    toUInt64(JSON_VALUE(value, '$.payload.after.loan_id')) AS loan_id,
    toFloat64(JSON_VALUE(value, '$.payload.after.payment_amount')) AS payment_amount,
    toFloat64(JSON_VALUE(value, '$.payload.after.principal_portion')) AS principal_portion,
    toFloat64(JSON_VALUE(value, '$.payload.after.interest_portion')) AS interest_portion,
    JSON_VALUE(value, '$.payload.after.payment_status') AS payment_status,
    JSON_VALUE(value, '$.payload.after.payment_channel') AS payment_channel,
    toDateTime64(toInt64(JSON_VALUE(value, '$.payload.after.payment_date')) / 1000000, 6) AS payment_date,
    toDateTime64(toInt64(JSON_VALUE(value, '$.payload.after.created_at')) / 1000000, 6) AS created_at
FROM raw_loan_payments
WHERE JSON_VALUE(value, '$.payload.after') IS NOT NULL;

CREATE MATERIALIZED VIEW mv_exchange_rates TO exchange_rates AS
SELECT
    toUInt64(JSON_VALUE(value, '$.payload.after.id')) AS id,
    JSON_VALUE(value, '$.payload.after.base_currency') AS base_currency,
    JSON_VALUE(value, '$.payload.after.target_currency') AS target_currency,
    toFloat64(JSON_VALUE(value, '$.payload.after.rate')) AS rate,
    addDays(toDate('1970-01-01'), toInt32(JSON_VALUE(value, '$.payload.after.effective_date'))) AS effective_date,
    toDateTime64(toInt64(JSON_VALUE(value, '$.payload.after.created_at')) / 1000000, 6) AS created_at
FROM raw_exchange_rates
WHERE JSON_VALUE(value, '$.payload.after') IS NOT NULL;
