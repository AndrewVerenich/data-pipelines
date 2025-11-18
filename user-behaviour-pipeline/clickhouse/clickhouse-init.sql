CREATE DATABASE IF NOT EXISTS analytics;

USE analytics;

-- 1) Events per type
CREATE TABLE kafka_events_per_type (
                                       metric String,
                                       key String,
                                       windowStart UInt64,
                                       windowEnd UInt64,
                                       value Float64
) ENGINE = Kafka
SETTINGS kafka_broker_list = 'kafka:9092',
         kafka_topic_list = 'events_per_type',
         kafka_group_name = 'ch_events_per_type',
         kafka_format = 'JSONEachRow';

CREATE TABLE events_per_type (
                                 metric String,
                                 key String,
                                 windowStart UInt64,
                                 windowEnd UInt64,
                                 value Float64
) ENGINE = MergeTree()
ORDER BY (metric, key, windowStart);

CREATE MATERIALIZED VIEW events_per_type_mv TO events_per_type AS
SELECT * FROM kafka_events_per_type;

-- 2) Page views
CREATE TABLE kafka_page_views (
                                  metric String,
                                  key String,
                                  windowStart UInt64,
                                  windowEnd UInt64,
                                  value Float64
) ENGINE = Kafka
SETTINGS kafka_broker_list = 'kafka:9092',
         kafka_topic_list = 'page_views',
         kafka_group_name = 'ch_page_views',
         kafka_format = 'JSONEachRow';

CREATE TABLE page_views (
                            metric String,
                            key String,
                            windowStart UInt64,
                            windowEnd UInt64,
                            value Float64
) ENGINE = MergeTree()
ORDER BY (metric, key, windowStart);

CREATE MATERIALIZED VIEW page_views_mv TO page_views AS
SELECT * FROM kafka_page_views;

-- 3) Unique users per page
CREATE TABLE kafka_unique_users_per_page (
                                             metric String,
                                             key String,
                                             windowStart UInt64,
                                             windowEnd UInt64,
                                             value Float64
) ENGINE = Kafka
SETTINGS kafka_broker_list = 'kafka:9092',
         kafka_topic_list = 'unique_users_per_page',
         kafka_group_name = 'ch_unique_users',
         kafka_format = 'JSONEachRow';

CREATE TABLE unique_users_per_page (
                                       metric String,
                                       key String,
                                       windowStart UInt64,
                                       windowEnd UInt64,
                                       value Float64
) ENGINE = MergeTree()
ORDER BY (metric, key, windowStart);

CREATE MATERIALIZED VIEW unique_users_per_page_mv TO unique_users_per_page AS
SELECT * FROM kafka_unique_users_per_page;

-- 4) Conversion rate
CREATE TABLE kafka_conversion_rate (
                                       metric String,
                                       key String,
                                       windowStart UInt64,
                                       windowEnd UInt64,
                                       value Float64
) ENGINE = Kafka
SETTINGS kafka_broker_list = 'kafka:9092',
         kafka_topic_list = 'conversion_rate',
         kafka_group_name = 'ch_conversion_rate',
         kafka_format = 'JSONEachRow';

CREATE TABLE conversion_rate (
                                 metric String,
                                 key String,
                                 windowStart UInt64,
                                 windowEnd UInt64,
                                 value Float64
) ENGINE = MergeTree()
ORDER BY (metric, key, windowStart);

CREATE MATERIALIZED VIEW conversion_rate_mv TO conversion_rate AS
SELECT * FROM kafka_conversion_rate;

-- 5) Session stats
CREATE TABLE kafka_session_stats (
                                     userId String,
                                     sessionStart UInt64,
                                     sessionEnd UInt64,
                                     durationMs UInt64,
                                     events UInt64
) ENGINE = Kafka
SETTINGS kafka_broker_list = 'kafka:9092',
         kafka_topic_list = 'session_stats',
         kafka_group_name = 'ch_session_stats',
         kafka_format = 'JSONEachRow';

CREATE TABLE session_stats (
                               userId String,
                               sessionStart UInt64,
                               sessionEnd UInt64,
                               durationMs UInt64,
                               events UInt64
) ENGINE = MergeTree()
ORDER BY (userId, sessionStart);

CREATE MATERIALIZED VIEW session_stats_mv TO session_stats AS
SELECT * FROM kafka_session_stats;

-- 6) Funnel stats
CREATE TABLE kafka_funnel_stats (
                                    windowStart UInt64,
                                    windowEnd UInt64,
                                    viewed UInt64,
                                    clicked UInt64,
                                    purchased UInt64,
                                    completedVC UInt64,
                                    completedCP UInt64,
                                    completedVCP UInt64
) ENGINE = Kafka
SETTINGS kafka_broker_list = 'kafka:9092',
         kafka_topic_list = 'funnel_stats',
         kafka_group_name = 'ch_funnel_stats',
         kafka_format = 'JSONEachRow';

CREATE TABLE funnel_stats (
                              windowStart UInt64,
                              windowEnd UInt64,
                              viewed UInt64,
                              clicked UInt64,
                              purchased UInt64,
                              completedVC UInt64,
                              completedCP UInt64,
                              completedVCP UInt64
) ENGINE = MergeTree()
ORDER BY (windowStart);

CREATE MATERIALIZED VIEW funnel_stats_mv TO funnel_stats AS
SELECT * FROM kafka_funnel_stats;

-- 7) Activity heatmap
CREATE TABLE kafka_activity_heatmap (
                                        metric String,
                                        key String,
                                        windowStart UInt64,
                                        windowEnd UInt64,
                                        value Float64
) ENGINE = Kafka
SETTINGS kafka_broker_list = 'kafka:9092',
         kafka_topic_list = 'activity_heatmap',
         kafka_group_name = 'ch_activity_heatmap',
         kafka_format = 'JSONEachRow';

CREATE TABLE activity_heatmap (
                                  metric String,
                                  key String,
                                  windowStart UInt64,
                                  windowEnd UInt64,
                                  value Float64
) ENGINE = MergeTree()
ORDER BY (metric, key, windowStart);

CREATE MATERIALIZED VIEW activity_heatmap_mv TO activity_heatmap AS
SELECT * FROM kafka_activity_heatmap;

-- 8) Retention
CREATE TABLE kafka_retention (
                                 windowStart UInt64,
                                 windowEnd UInt64,
                                 returningUsers UInt64,
                                 newUsers UInt64,
                                 totalUsers UInt64
) ENGINE = Kafka
SETTINGS kafka_broker_list = 'kafka:9092',
         kafka_topic_list = 'retention',
         kafka_group_name = 'ch_retention',
         kafka_format = 'JSONEachRow';

CREATE TABLE retention (
                           windowStart UInt64,
                           windowEnd UInt64,
                           returningUsers UInt64,
                           newUsers UInt64,
                           totalUsers UInt64
) ENGINE = MergeTree()
ORDER BY (windowStart);

CREATE MATERIALIZED VIEW retention_mv TO retention AS
SELECT * FROM kafka_retention;
