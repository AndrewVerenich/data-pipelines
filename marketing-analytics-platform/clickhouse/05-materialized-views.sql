-- MATERIALIZED VIEWS: fact_events -> aggregated tables

CREATE DATABASE IF NOT EXISTS marketing;

-- fact_events -> daily_user_activity
CREATE MATERIALIZED VIEW IF NOT EXISTS marketing.mv_daily_user_activity TO marketing.daily_user_activity AS
SELECT
    event_date,
    event_source,
    event_type,
    count()        AS events_count,
    uniq(user_id)  AS unique_users,
    sum(revenue)   AS total_revenue
FROM marketing.fact_events
GROUP BY event_date, event_source, event_type;

-- fact_events -> conversion_funnel_daily
CREATE MATERIALIZED VIEW IF NOT EXISTS marketing.mv_conversion_funnel TO marketing.conversion_funnel_daily AS
SELECT
    event_date,
    uniqState(if(event_type = 'page_view',   user_id, toUInt64(0))) AS page_viewers,
    uniqState(if(event_type = 'click',        user_id, toUInt64(0))) AS clickers,
    uniqState(if(event_type = 'add_to_cart',  user_id, toUInt64(0))) AS cart_adders,
    uniqState(if(event_type = 'purchase',     user_id, toUInt64(0))) AS purchasers
FROM marketing.fact_events
WHERE event_source = 'website'
GROUP BY event_date;

-- fact_events -> campaign_performance_daily
CREATE MATERIALIZED VIEW IF NOT EXISTS marketing.mv_campaign_performance TO marketing.campaign_performance_daily AS
SELECT
    event_date,
    assumeNotNull(campaign_id)            AS campaign_id,
    channel                               AS platform,
    countIf(event_type = 'impression')    AS impressions,
    countIf(event_type = 'click')         AS clicks,
    countIf(event_type = 'conversion')    AS conversions,
    sum(cost)                             AS total_cost,
    sum(revenue)                          AS total_revenue
FROM marketing.fact_events
WHERE event_source = 'ad_platform' AND campaign_id IS NOT NULL
GROUP BY event_date, campaign_id, channel;

-- fact_events -> user_ltv
CREATE MATERIALIZED VIEW IF NOT EXISTS marketing.mv_user_ltv TO marketing.user_ltv AS
SELECT
    user_id,
    sum(revenue)            AS total_revenue,
    toUInt64(count())       AS order_count,
    min(event_timestamp)    AS first_purchase,
    max(event_timestamp)    AS last_purchase
FROM marketing.fact_events
WHERE event_type IN ('purchase', 'order_completed')
GROUP BY user_id;
