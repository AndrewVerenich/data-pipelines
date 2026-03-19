-- VIEWS: merged/scalar representations for BI (Superset) — one row per key, no AggregateFunction in result

CREATE DATABASE IF NOT EXISTS marketing;

-- One row per event_date with correct uniq cardinalities (merge AggregateFunction states per date)
CREATE VIEW IF NOT EXISTS marketing.conversion_funnel_daily_merged AS
SELECT
    event_date,
    uniqMerge(page_viewers) AS page_viewers,
    uniqMerge(clickers)    AS clickers,
    uniqMerge(cart_adders) AS cart_adders,
    uniqMerge(purchasers)  AS purchasers
FROM marketing.conversion_funnel_daily
GROUP BY event_date;

-- One row per user with final LTV (FINAL merges parts so SimpleAggregateFunction columns are scalar)
CREATE VIEW IF NOT EXISTS marketing.user_ltv_final AS
SELECT
    user_id,
    total_revenue,
    order_count,
    first_purchase,
    last_purchase
FROM marketing.user_ltv FINAL;

-- DAU по (date, source): один раз считаем uniq(user_id), без суммы по типам событий (иначе один пользователь учитывается несколько раз)
CREATE VIEW IF NOT EXISTS marketing.daily_active_users_by_source AS
SELECT
    event_date,
    event_source,
    uniq(user_id) AS unique_users
FROM marketing.fact_events
GROUP BY event_date, event_source;

-- DAU trend (same metric as 07-marketing-metrics.sql grouped by date)
CREATE VIEW IF NOT EXISTS marketing.dau_daily AS
SELECT
    event_date,
    uniqExact(user_id) AS dau
FROM marketing.fact_events
GROUP BY event_date;

-- MAU snapshot
CREATE VIEW IF NOT EXISTS marketing.mau_snapshot AS
SELECT
    uniqExact(user_id) AS mau
FROM marketing.fact_events
WHERE event_date >= today() - 30;

-- Conversion rate by date
CREATE VIEW IF NOT EXISTS marketing.conversion_rate_daily AS
SELECT
    event_date,
    uniqMerge(page_viewers) AS viewers,
    uniqMerge(clickers) AS clickers,
    uniqMerge(cart_adders) AS cart_adders,
    uniqMerge(purchasers) AS buyers,
    if(uniqMerge(page_viewers) > 0, uniqMerge(purchasers) / uniqMerge(page_viewers), 0) AS conversion_rate
FROM marketing.conversion_funnel_daily
GROUP BY event_date;

-- CAC by platform
CREATE OR REPLACE VIEW marketing.cac_by_platform AS
SELECT
    dc.platform AS platform,
    sum(cp.total_cost) AS total_spend,
    uniqExact(fe.user_id) AS acquired_users,
    if(uniqExact(fe.user_id) > 0, sum(cp.total_cost) / uniqExact(fe.user_id), 0) AS cac
FROM marketing.campaign_performance_daily cp
INNER JOIN marketing.dim_campaigns dc ON cp.campaign_sk = dc.campaign_sk
INNER JOIN marketing.fact_events fe
    ON fe.campaign_sk = cp.campaign_sk
    AND fe.event_type = 'registration'
GROUP BY dc.platform;

-- ROAS by campaign
CREATE OR REPLACE VIEW marketing.roas_by_campaign AS
SELECT
    cp.campaign_id AS campaign_id,
    dc.name AS campaign_name,
    dc.platform AS platform,
    sum(cp.total_revenue) AS revenue,
    sum(cp.total_cost) AS cost,
    if(sum(cp.total_cost) > 0, sum(cp.total_revenue) / sum(cp.total_cost), 0) AS roas
FROM marketing.campaign_performance_daily cp
INNER JOIN marketing.dim_campaigns dc ON cp.campaign_sk = dc.campaign_sk
GROUP BY cp.campaign_id, dc.name, dc.platform;

-- Daily revenue (purchase/order_completed)
CREATE VIEW IF NOT EXISTS marketing.revenue_daily AS
SELECT
    event_date,
    sum(total_revenue) AS revenue
FROM marketing.daily_user_activity
WHERE event_type IN ('purchase', 'order_completed')
GROUP BY event_date;

-- Revenue by channel
CREATE VIEW IF NOT EXISTS marketing.revenue_by_channel AS
SELECT
    channel,
    sum(revenue) AS total_revenue,
    sum(cost) AS total_cost,
    sum(revenue) - sum(cost) AS profit
FROM marketing.fact_events
WHERE event_source = 'ad_platform'
GROUP BY channel;

-- ARPU by day
CREATE VIEW IF NOT EXISTS marketing.arpu_daily AS
SELECT
    event_date,
    sum(revenue) AS total_revenue,
    uniqExact(user_id) AS paying_users,
    if(uniqExact(user_id) > 0, sum(revenue) / uniqExact(user_id), 0) AS arpu
FROM marketing.fact_events
WHERE event_type IN ('purchase', 'order_completed')
GROUP BY event_date;

-- LTV top users
CREATE VIEW IF NOT EXISTS marketing.ltv_top_users AS
SELECT
    u.user_id,
    u.total_revenue,
    u.order_count,
    u.first_purchase,
    u.last_purchase,
    if(u.order_count > 0, u.total_revenue / u.order_count, 0) AS avg_order_value,
    dateDiff('day', u.first_purchase, u.last_purchase) AS customer_lifespan_days
FROM marketing.user_ltv AS u FINAL;

-- LTV + current user dimensions
CREATE OR REPLACE VIEW marketing.ltv_users_current_dim AS
SELECT
    du.user_id AS user_id,
    du.user_sk AS user_sk,
    du.name,
    du.acquisition_channel,
    du.segment,
    u.total_revenue,
    u.order_count,
    if(u.order_count > 0, u.total_revenue / u.order_count, 0) AS avg_order_value
FROM marketing.user_ltv AS u FINAL
INNER JOIN marketing.dim_users AS du
    ON u.user_id = du.user_id
    AND du.is_current = 1;

-- LTV + historical segment at first purchase
CREATE VIEW IF NOT EXISTS marketing.ltv_users_historical_dim AS
SELECT
    du.user_id,
    du.name,
    du.segment AS segment_at_first_purchase,
    du.acquisition_channel,
    u.total_revenue,
    u.order_count
FROM marketing.user_ltv AS u FINAL
INNER JOIN marketing.dim_users AS du
    ON u.user_id = du.user_id
WHERE du.valid_from <= u.first_purchase
  AND u.first_purchase < du.valid_to;

-- LTV performance by current segment
CREATE VIEW IF NOT EXISTS marketing.ltv_segments_performance AS
SELECT
    du.segment,
    count() AS users,
    sum(u.total_revenue) AS total_revenue,
    avg(u.total_revenue) AS avg_ltv,
    avg(u.order_count) AS avg_orders
FROM marketing.user_ltv AS u FINAL
INNER JOIN marketing.dim_users AS du
    ON u.user_id = du.user_id
    AND du.is_current = 1
GROUP BY du.segment;
