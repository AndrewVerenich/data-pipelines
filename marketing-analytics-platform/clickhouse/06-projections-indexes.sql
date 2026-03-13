-- PROJECTIONS: pre-aggregated / re-sorted alternative representations

CREATE DATABASE IF NOT EXISTS marketing;

ALTER TABLE marketing.fact_events ADD PROJECTION IF NOT EXISTS proj_by_campaign (
    SELECT
        coalesce(campaign_id, toUInt32(0)) AS campaign_id_key,
        event_date,
        event_type,
        count() AS cnt,
        sum(revenue) AS rev,
        sum(cost) AS cst
    GROUP BY campaign_id_key, event_date, event_type
);

ALTER TABLE marketing.fact_events ADD PROJECTION IF NOT EXISTS proj_by_user_date (
    SELECT user_id, event_date, event_type,
           count() AS cnt, sum(revenue) AS rev
    GROUP BY user_id, event_date, event_type
);

ALTER TABLE marketing.fact_events MATERIALIZE PROJECTION proj_by_campaign;
ALTER TABLE marketing.fact_events MATERIALIZE PROJECTION proj_by_user_date;

-- DATA SKIPPING INDEXES

ALTER TABLE marketing.fact_events ADD INDEX IF NOT EXISTS idx_campaign_id campaign_id
    TYPE set(100) GRANULARITY 4;

ALTER TABLE marketing.fact_events ADD INDEX IF NOT EXISTS idx_user_id user_id
    TYPE bloom_filter(0.01) GRANULARITY 4;

ALTER TABLE marketing.fact_events ADD INDEX IF NOT EXISTS idx_event_date event_date
    TYPE minmax GRANULARITY 1;

ALTER TABLE marketing.fact_events MATERIALIZE INDEX idx_campaign_id;
ALTER TABLE marketing.fact_events MATERIALIZE INDEX idx_user_id;
ALTER TABLE marketing.fact_events MATERIALIZE INDEX idx_event_date;
