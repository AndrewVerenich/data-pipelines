CREATE VIEW IF NOT EXISTS logistics.v_trip_sla AS
WITH
    events AS (
        SELECT
            trip_id,
            any(courier_id) AS courier_id,
            any(warehouse_id) AS warehouse_id,
            minIf(promised_eta, event_type = 'ASSIGNED') AS promised_eta,
            minIf(event_time, event_type = 'ASSIGNED') AS assigned_at,
            minIf(event_time, event_type = 'AT_WAREHOUSE') AS at_warehouse_at,
            minIf(event_time, event_type = 'EN_ROUTE_DROP') AS left_warehouse_at,
            minIf(event_time, event_type = 'DELIVERED') AS delivered_at,
            argMax(event_type, event_time) AS last_event
        FROM logistics.raw_trip_events
        GROUP BY trip_id
    ),
    last_ping AS (
        SELECT
            trip_id,
            argMax(lat, event_time) AS lat,
            argMax(lon, event_time) AS lon,
            max(event_time) AS last_ping_at
        FROM logistics.raw_gps_pings
        GROUP BY trip_id
    )
SELECT
    e.trip_id AS trip_id,
    e.courier_id AS courier_id,
    e.warehouse_id AS warehouse_id,
    e.promised_eta AS promised_eta,
    e.assigned_at AS assigned_at,
    e.at_warehouse_at AS at_warehouse_at,
    e.left_warehouse_at AS left_warehouse_at,
    e.delivered_at AS delivered_at,
    e.last_event AS last_event,
    if(
        e.delivered_at > toDateTime64(0, 3),
        dateDiff('second', e.promised_eta, e.delivered_at),
        if(now64(3) > e.promised_eta AND e.last_event != 'DELIVERED', dateDiff('second', e.promised_eta, now64(3)), 0)
    ) AS late_sec,
    if(
        e.delivered_at > toDateTime64(0, 3),
        e.delivered_at > e.promised_eta,
        now64(3) > e.promised_eta AND e.last_event != 'DELIVERED'
    ) AS sla_breached,
    if(
        e.at_warehouse_at > toDateTime64(0, 3) AND e.left_warehouse_at > toDateTime64(0, 3),
        dateDiff('second', e.at_warehouse_at, e.left_warehouse_at),
        if(e.at_warehouse_at > toDateTime64(0, 3) AND e.left_warehouse_at = toDateTime64(0, 3),
           dateDiff('second', e.at_warehouse_at, now64(3)),
           0)
    ) AS dwell_sec,
    p.lat AS lat,
    p.lon AS lon,
    p.last_ping_at AS last_ping_at
FROM events AS e
LEFT JOIN last_ping AS p ON e.trip_id = p.trip_id;

CREATE VIEW IF NOT EXISTS logistics.v_sla_breaches AS
SELECT
    trip_id,
    courier_id,
    warehouse_id,
    promised_eta,
    delivered_at,
    late_sec,
    last_event,
    lat,
    lon
FROM logistics.v_trip_sla
WHERE sla_breached = 1
ORDER BY late_sec DESC;

CREATE VIEW IF NOT EXISTS logistics.v_warehouse_dwell AS
SELECT
    trip_id,
    courier_id,
    warehouse_id,
    at_warehouse_at,
    left_warehouse_at,
    dwell_sec,
    dwell_sec >= 600 AS long_dwell,
    last_event
FROM logistics.v_trip_sla
WHERE dwell_sec > 0
ORDER BY dwell_sec DESC;

CREATE VIEW IF NOT EXISTS logistics.v_active_positions AS
SELECT
    trip_id,
    courier_id,
    warehouse_id,
    lat,
    lon,
    last_event,
    sla_breached,
    late_sec,
    dwell_sec,
    last_ping_at
FROM logistics.v_trip_sla
WHERE last_event != 'DELIVERED'
  AND last_ping_at >= now() - INTERVAL 30 MINUTE;

CREATE VIEW IF NOT EXISTS logistics.v_ops_summary AS
SELECT
    countIf(last_event != 'DELIVERED') AS active_trips,
    countIf(sla_breached = 1) AS sla_breaches,
    countIf(delivered_at > toDateTime64(0, 3)) AS delivered_trips,
    countIf(delivered_at > toDateTime64(0, 3) AND sla_breached = 0) AS on_time_deliveries,
    if(
        countIf(delivered_at > toDateTime64(0, 3)) = 0,
        0.0,
        countIf(delivered_at > toDateTime64(0, 3) AND sla_breached = 0)
            / countIf(delivered_at > toDateTime64(0, 3))
    ) AS on_time_rate,
    avgIf(dwell_sec, dwell_sec > 0) AS avg_dwell_sec
FROM logistics.v_trip_sla;
