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
