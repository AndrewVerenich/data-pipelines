-- DIMENSION TABLES: ReplacingMergeTree (simple SCD via last version)

CREATE DATABASE IF NOT EXISTS marketing;

CREATE TABLE IF NOT EXISTS marketing.dim_users (
    user_id              UInt64,
    name                 String,
    email                String,
    signup_date          Date,
    acquisition_channel  LowCardinality(String),
    segment              LowCardinality(String),
    updated_at           DateTime DEFAULT now()
) ENGINE = ReplacingMergeTree(updated_at)
ORDER BY user_id;

CREATE TABLE IF NOT EXISTS marketing.dim_products (
    product_id   UInt32,
    name         String,
    category     LowCardinality(String),
    price        Decimal(18,2),
    updated_at   DateTime DEFAULT now()
) ENGINE = ReplacingMergeTree(updated_at)
ORDER BY product_id;

CREATE TABLE IF NOT EXISTS marketing.dim_campaigns (
    campaign_id  UInt32,
    name         String,
    platform     LowCardinality(String),
    budget       Decimal(18,2),
    spent        Decimal(18,2),
    start_date   Date,
    end_date     Date,
    updated_at   DateTime DEFAULT now()
) ENGINE = ReplacingMergeTree(updated_at)
ORDER BY campaign_id;

-- SEED DATA

INSERT INTO marketing.dim_users (user_id, name, email, signup_date, acquisition_channel, segment)
SELECT
    toUInt64(number + 1) AS user_id,
    concat('User ', toString(number + 1)) AS name,
    concat('user', toString(number + 1), '@example.com') AS email,
    addDays(toDate('2024-01-01'), number) AS signup_date,
    arrayElement(['organic', 'paid_search', 'social', 'referral'], (number % 4) + 1) AS acquisition_channel,
    arrayElement(['new', 'active', 'vip', 'churned'], (number % 4) + 1) AS segment
FROM numbers(100);


INSERT INTO marketing.dim_products (product_id, name, category, price)
SELECT
    toUInt32(number + 1) AS product_id,
    concat('Product ', toString(number + 1)) AS name,
    arrayElement(['electronics', 'accessories', 'sports', 'clothing', 'home'], (number % 5) + 1) AS category,
    toDecimal64(9.99 + number * 5.5, 2) AS price
FROM numbers(50);


INSERT INTO marketing.dim_campaigns (campaign_id, name, platform, budget, spent, start_date, end_date)
SELECT
    toUInt32(number + 1) AS campaign_id,
    concat('Campaign ', toString(number + 1)) AS name,
    arrayElement(['google', 'facebook', 'tiktok', 'instagram'], (number % 4) + 1) AS platform,
    toDecimal64(2000 + number * 250, 2) AS budget,
    toDecimal64(1200 + number * 175, 2) AS spent,
    addDays(toDate('2025-01-01'), number * 7) AS start_date,
    addDays(toDate('2025-01-14'), number * 7) AS end_date
FROM numbers(20);