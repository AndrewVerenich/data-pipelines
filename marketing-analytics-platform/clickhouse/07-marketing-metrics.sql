-- MARKETING & PRODUCT METRICS

USE marketing;

-- DAU (Daily Active Users)
SELECT
    event_date,
    uniqExact(user_id) AS dau
FROM fact_events
WHERE event_date = today()
GROUP BY event_date;

-- MAU (Monthly Active Users)
SELECT uniqExact(user_id) AS mau
FROM fact_events
WHERE event_date >= today() - 30;

-- DAU / MAU Trend (last 90 days)
SELECT
    event_date,
    uniqExact(user_id) AS dau
FROM fact_events
WHERE event_date >= today() - 90
GROUP BY event_date
ORDER BY event_date;

-- Conversion Rate (from pre-aggregated funnel)
SELECT
    event_date,
    uniqMerge(page_viewers) AS viewers,
    uniqMerge(clickers)     AS clickers,
    uniqMerge(cart_adders)  AS cart_adders,
    uniqMerge(purchasers)   AS buyers,
    if(uniqMerge(page_viewers) > 0,
       uniqMerge(purchasers) / uniqMerge(page_viewers), 0) AS conversion_rate
FROM conversion_funnel_daily
GROUP BY event_date
ORDER BY event_date;

-- CAC (Customer Acquisition Cost) per platform
SELECT
    dc.platform,
    sum(cp.total_cost) AS total_spend,
    uniqExact(fe.user_id) AS acquired_users,
    if(uniqExact(fe.user_id) > 0,
       sum(cp.total_cost) / uniqExact(fe.user_id), 0) AS cac
FROM campaign_performance_daily cp
INNER JOIN dim_campaigns dc
    ON cp.campaign_id = dc.campaign_id
    AND dc.is_current = 1
INNER JOIN fact_events fe
    ON fe.campaign_id = cp.campaign_id
    AND fe.event_type = 'registration'
GROUP BY dc.platform
ORDER BY cac;

-- ROAS (Return on Ad Spend) per campaign
SELECT
    cp.campaign_id,
    dc.name AS campaign_name,
    dc.platform,
    sum(cp.total_revenue) AS revenue,
    sum(cp.total_cost) AS cost,
    if(sum(cp.total_cost) > 0,
       sum(cp.total_revenue) / sum(cp.total_cost), 0) AS roas
FROM campaign_performance_daily cp
INNER JOIN dim_campaigns dc
    ON cp.campaign_id = dc.campaign_id
    AND dc.is_current = 1
GROUP BY cp.campaign_id, dc.name, dc.platform
ORDER BY roas DESC;

-- Daily Revenue
SELECT
    event_date,
    sum(total_revenue) AS revenue
FROM daily_user_activity
WHERE event_type IN ('purchase', 'order_completed')
GROUP BY event_date
ORDER BY event_date;

-- Revenue by Channel (from ad platform events)
SELECT
    channel,
    sum(revenue) AS total_revenue,
    sum(cost) AS total_cost,
    sum(revenue) - sum(cost) AS profit
FROM fact_events
WHERE event_source = 'ad_platform'
GROUP BY channel
ORDER BY total_revenue DESC;

-- ARPU (Average Revenue Per User) — daily
SELECT
    event_date,
    sum(revenue) AS total_revenue,
    uniqExact(user_id) AS paying_users,
    if(uniqExact(user_id) > 0,
       sum(revenue) / uniqExact(user_id), 0) AS arpu
FROM fact_events
WHERE event_type IN ('purchase', 'order_completed')
GROUP BY event_date
ORDER BY event_date;

-- LTV (Lifetime Value) — top 100 users
SELECT
    u.user_id,
    u.total_revenue,
    u.order_count,
    u.first_purchase,
    u.last_purchase,
    if(u.order_count > 0,
       u.total_revenue / u.order_count, 0) AS avg_order_value,
    dateDiff('day', u.first_purchase, u.last_purchase) AS customer_lifespan_days
FROM user_ltv AS u FINAL
ORDER BY u.total_revenue DESC
LIMIT 100;

-- LTV joined with user dimensions (SCD Type 2: current version)
SELECT
    du.user_id,
    du.user_sk,
    du.name,
    du.acquisition_channel,
    du.segment,
    u.total_revenue,
    u.order_count,
    if(u.order_count > 0,
       u.total_revenue / u.order_count, 0) AS avg_order_value
FROM user_ltv AS u FINAL
INNER JOIN dim_users AS du
    ON u.user_id = du.user_id
    AND du.is_current = 1
ORDER BY u.total_revenue DESC
LIMIT 50;

-- LTV with historical dimension: segment at the time of first purchase
SELECT
    du.user_id,
    du.name,
    du.segment AS segment_at_first_purchase,
    du.acquisition_channel,
    u.total_revenue,
    u.order_count
FROM user_ltv AS u FINAL
INNER JOIN dim_users AS du
    ON u.user_id = du.user_id
    AND du.valid_from <= u.first_purchase
    AND u.first_purchase < du.valid_to
ORDER BY u.total_revenue DESC
LIMIT 50;

-- User Segments Performance (current segment)
SELECT
    du.segment,
    count() AS users,
    sum(u.total_revenue) AS total_revenue,
    avg(u.total_revenue) AS avg_ltv,
    avg(u.order_count) AS avg_orders
FROM user_ltv AS u FINAL
INNER JOIN dim_users AS du
    ON u.user_id = du.user_id
    AND du.is_current = 1
GROUP BY du.segment
ORDER BY total_revenue DESC;
