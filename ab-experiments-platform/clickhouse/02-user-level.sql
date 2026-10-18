CREATE VIEW IF NOT EXISTS ab.user_level AS
SELECT
    f.experiment_id AS experiment_id,
    f.experiment_key AS experiment_key,
    f.variant_key AS variant_key,
    f.user_id AS user_id,
    f.bucket AS bucket,
    f.exposed_at AS exposed_at,
    toUInt8(countIf(
        o.event_type = 'conversion'
        AND o.event_time >= f.exposed_at
        AND o.event_time < f.exposed_at + INTERVAL 1 HOUR
    ) > 0) AS converted,
    sumIf(
        o.value,
        o.event_type = 'revenue'
        AND o.event_time >= f.exposed_at
        AND o.event_time < f.exposed_at + INTERVAL 1 HOUR
    ) AS revenue,
    toUInt8(countIf(
        o.event_type = 'refund'
        AND o.event_time >= f.exposed_at
        AND o.event_time < f.exposed_at + INTERVAL 1 HOUR
    ) > 0) AS refunded,
    sumIf(
        o.value,
        o.event_type = 'revenue'
        AND o.event_time >= f.exposed_at - INTERVAL 7 DAY
        AND o.event_time < f.exposed_at
    ) AS pre_revenue
FROM ab.raw_exposures AS f FINAL
LEFT JOIN ab.raw_outcomes AS o ON o.user_id = f.user_id
GROUP BY
    f.experiment_id,
    f.experiment_key,
    f.variant_key,
    f.user_id,
    f.bucket,
    f.exposed_at;
