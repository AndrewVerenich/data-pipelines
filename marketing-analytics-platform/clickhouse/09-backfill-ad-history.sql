-- MANUAL BACKFILL: Ad platform history for dashboard visibility

INSERT INTO marketing.raw_ad_events (
    event_id,
    campaign_id,
    platform,
    event_type,
    cost,
    revenue,
    user_id,
    timestamp,
    event_date
)
SELECT
    generated.event_id,
    generated.campaign_id,
    generated.platform,
    generated.event_type,
    generated.cost,
    generated.revenue,
    generated.user_id,
    generated.timestamp,
    toDate(generated.timestamp) AS event_date
FROM
(
    SELECT
        concat('bfa_', toString(day_offset), '_', toString(event_no)) AS event_id,
        toUInt32(1 + (event_no % 20)) AS campaign_id,
        arrayElement(['google', 'facebook', 'instagram', 'tiktok'], 1 + (event_no % 4)) AS platform,
        multiIf(
            event_no % 100 < 64, 'impression',
            event_no % 100 < 88, 'click',
            'conversion'
        ) AS event_type,
        toDecimal64(
            multiIf(
                event_type = 'impression', 0.02 + (event_no % 7) * 0.01,
                event_type = 'click', 0.35 + (event_no % 9) * 0.18,
                4.50 + (event_no % 13) * 0.95
            ),
            2
        ) AS cost,
        if(
            event_type = 'conversion',
            CAST(
                toDecimal64(
                    60 + (day_offset % 11) * 8 + (event_no % 17) * 4 + (event_no % 100) / 100.0,
                    2
                ) AS Nullable(Decimal(18,2))
            ),
            CAST(NULL AS Nullable(Decimal(18,2)))
        ) AS revenue,
        if(
            event_type = 'impression',
            CAST(NULL AS Nullable(UInt64)),
            CAST(toUInt64(1 + (cityHash64(concat('ad-user-', toString(day_offset), '-', toString(event_no))) % 100)) AS Nullable(UInt64))
        ) AS user_id,
        toDateTime64(
            addSeconds(
                toDateTime(addDays(today(), -toInt32(45 - day_offset))),
                7 * 3600 + (event_no * 300) + ((day_offset * 53) % 240)
            ),
            3
        ) AS timestamp
    FROM
    (
        SELECT
            intDiv(number, 2500) AS day_offset,
            number % 2500 AS event_no
        FROM numbers(45 * 2500)
    )
) AS generated
WHERE generated.event_id NOT IN
(
    SELECT event_id
    FROM marketing.raw_ad_events
    WHERE startsWith(event_id, 'bfa_')
);
