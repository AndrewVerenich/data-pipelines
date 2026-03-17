-- DIMENSION TABLES: SCD Type 2 (surrogate key + valid_from/valid_to/is_current)

CREATE DATABASE IF NOT EXISTS marketing;

CREATE TABLE IF NOT EXISTS marketing.dim_users (
    user_sk              Int64,
    user_id              UInt64,
    name                 String,
    email                String,
    signup_date          Date,
    acquisition_channel  LowCardinality(String),
    segment              LowCardinality(String),
    valid_from           DateTime,
    valid_to             DateTime,
    is_current           UInt8
) ENGINE = MergeTree()
ORDER BY (user_id, valid_from);

CREATE TABLE IF NOT EXISTS marketing.dim_products (
    product_sk   Int64,
    product_id   UInt32,
    name         String,
    category     LowCardinality(String),
    price        Decimal(18,2),
    valid_from   DateTime,
    valid_to     DateTime,
    is_current   UInt8
) ENGINE = MergeTree()
ORDER BY (product_id, valid_from);

CREATE TABLE IF NOT EXISTS marketing.dim_campaigns (
    campaign_sk  Int64,
    campaign_id  UInt32,
    name         String,
    platform     LowCardinality(String),
    budget       Decimal(18,2),
    spent        Decimal(18,2),
    start_date   Date,
    end_date     Date,
    valid_from   DateTime,
    valid_to     DateTime,
    is_current   UInt8
) ENGINE = MergeTree()
ORDER BY (campaign_id, valid_from);

-- SEED DATA (single version per row: valid_from = epoch, valid_to = open end, is_current = 1)

INSERT INTO marketing.dim_users (user_sk, user_id, name, email, signup_date, acquisition_channel, segment, valid_from, valid_to, is_current)
SELECT
    toInt64(cityHash64(toUInt64(number + 1), toDateTime('2024-01-01 00:00:00'))) AS user_sk,
    toUInt64(number + 1) AS user_id,
    concat('User ', toString(number + 1)) AS name,
    concat('user', toString(number + 1), '@example.com') AS email,
    addDays(toDate('2024-01-01'), number) AS signup_date,
    arrayElement(['organic', 'paid_search', 'social', 'referral'], (number % 4) + 1) AS acquisition_channel,
    arrayElement(['new', 'active', 'vip', 'churned'], (number % 4) + 1) AS segment,
    toDateTime('2024-01-01 00:00:00') AS valid_from,
    toDateTime('2099-12-31 23:59:59') AS valid_to,
    toUInt8(1) AS is_current
FROM numbers(100);

INSERT INTO marketing.dim_products (product_sk, product_id, name, category, price, valid_from, valid_to, is_current)
SELECT
    toInt64(cityHash64(toUInt32(number + 1), toDateTime('2024-01-01 00:00:00'))) AS product_sk,
    toUInt32(number + 1) AS product_id,
    concat('Product ', toString(number + 1)) AS name,
    arrayElement(['electronics', 'accessories', 'sports', 'clothing', 'home'], (number % 5) + 1) AS category,
    toDecimal64(9.99 + number * 5.5, 2) AS price,
    toDateTime('2024-01-01 00:00:00') AS valid_from,
    toDateTime('2099-12-31 23:59:59') AS valid_to,
    toUInt8(1) AS is_current
FROM numbers(50);

INSERT INTO marketing.dim_campaigns (campaign_sk, campaign_id, name, platform, budget, spent, start_date, end_date, valid_from, valid_to, is_current)
SELECT
    toInt64(cityHash64(toUInt32(number + 1), toDateTime('2025-01-01 00:00:00'))) AS campaign_sk,
    toUInt32(number + 1) AS campaign_id,
    concat('Campaign ', toString(number + 1)) AS name,
    arrayElement(['google', 'facebook', 'tiktok', 'instagram'], (number % 4) + 1) AS platform,
    toDecimal64(2000 + number * 250, 2) AS budget,
    toDecimal64(1200 + number * 175, 2) AS spent,
    addDays(toDate('2025-01-01'), number * 7) AS start_date,
    addDays(toDate('2025-01-14'), number * 7) AS end_date,
    toDateTime('2025-01-01 00:00:00') AS valid_from,
    toDateTime('2099-12-31 23:59:59') AS valid_to,
    toUInt8(1) AS is_current
FROM numbers(20);