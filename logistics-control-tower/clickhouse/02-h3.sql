CREATE TABLE IF NOT EXISTS logistics.agg_h3_demand (
    window_start DateTime,
    h3_index     UInt64,
    ping_count   UInt64,
    trip_count   UInt64
) ENGINE = SummingMergeTree
PARTITION BY toYYYYMMDD(window_start)
ORDER BY (window_start, h3_index)
TTL window_start + INTERVAL 14 DAY;

CREATE MATERIALIZED VIEW IF NOT EXISTS logistics.mv_h3_demand TO logistics.agg_h3_demand AS
SELECT
    toStartOfFifteenMinutes(event_time) AS window_start,
    geoToH3(lon, lat, 9) AS h3_index,
    toUInt64(1) AS ping_count,
    toUInt64(0) AS trip_count
FROM logistics.raw_gps_pings;

CREATE VIEW IF NOT EXISTS logistics.v_h3_demand_recent AS
SELECT
    h3_index,
    sum(ping_count) AS pings,
    -- ClickHouse h3ToGeo returns (lon, lat)
    h3ToGeo(h3_index).2 AS lat,
    h3ToGeo(h3_index).1 AS lon
FROM logistics.agg_h3_demand
WHERE window_start >= now() - INTERVAL 60 MINUTE
GROUP BY h3_index
HAVING pings > 0
ORDER BY pings DESC;
