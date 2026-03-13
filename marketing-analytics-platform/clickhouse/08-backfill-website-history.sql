-- MANUAL BACKFILL: Website history for dashboard visibility

INSERT INTO marketing.raw_website_events (
    event_id,
    user_id,
    event_type,
    page_url,
    product_id,
    revenue,
    session_id,
    timestamp,
    event_date
)
SELECT
    generated.event_id,
    generated.user_id,
    generated.event_type,
    generated.page_url,
    generated.product_id,
    generated.revenue,
    generated.session_id,
    generated.timestamp,
    toDate(generated.timestamp) AS event_date
FROM
(
    SELECT
        concat('bfw_', toString(day_offset), '_', toString(event_no)) AS event_id,
        toUInt64(1 + (cityHash64(concat('website-user-', toString(day_offset), '-', toString(event_no))) % 100)) AS user_id,
        multiIf(
            event_no % 100 < 42, 'page_view',
            event_no % 100 < 68, 'click',
            event_no % 100 < 83, 'add_to_cart',
            event_no % 100 < 93, 'purchase',
            'signup'
        ) AS event_type,
        multiIf(
            event_no % 5 = 0, '/home',
            event_no % 5 = 1, '/catalog',
            event_no % 5 = 2, '/product/' || toString(1 + (event_no % 50)),
            event_no % 5 = 3, '/campaign/spring-sale',
            '/checkout'
        ) AS page_url,
        if(event_type IN ('page_view', 'signup'), CAST(NULL AS Nullable(UInt32)), toUInt32(1 + (event_no % 50))) AS product_id,
        if(
            event_type = 'purchase',
            CAST(
                toDecimal64(
                    25 + (day_offset % 9) * 7 + (event_no % 11) * 3 + (event_no % 100) / 100.0,
                    2
                ) AS Nullable(Decimal(18,2))
            ),
            CAST(NULL AS Nullable(Decimal(18,2)))
        ) AS revenue,
        concat('bfw_session_', toString(day_offset), '_', toString(intDiv(event_no, 4))) AS session_id,
        toDateTime64(
            addSeconds(
                toDateTime(addDays(today(), -toInt32(45 - day_offset))),
                8 * 3600 + (event_no * 240) + ((day_offset * 37) % 180)
            ),
            3
        ) AS timestamp
    FROM
    (
        SELECT
            intDiv(number, 4000) AS day_offset,
            number % 4000 AS event_no
        FROM numbers(45 * 4000)
    )
) AS generated
WHERE generated.event_id NOT IN
(
    SELECT event_id
    FROM marketing.raw_website_events
    WHERE startsWith(event_id, 'bfw_')
);
