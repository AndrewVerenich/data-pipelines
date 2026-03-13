-- MANUAL BACKFILL: Backend history for dashboard visibility

INSERT INTO marketing.raw_backend_events (
    event_id,
    user_id,
    event_type,
    order_id,
    product_id,
    amount,
    timestamp,
    event_date
)
SELECT
    generated.event_id,
    generated.user_id,
    generated.event_type,
    generated.order_id,
    generated.product_id,
    generated.amount,
    generated.timestamp,
    toDate(generated.timestamp) AS event_date
FROM
(
    SELECT
        concat('bfb_', toString(day_offset), '_', toString(event_no)) AS event_id,
        toUInt64(1 + (cityHash64(concat('backend-user-', toString(day_offset), '-', toString(event_no))) % 100)) AS user_id,
        multiIf(
            event_no % 100 < 18, 'registration',
            event_no % 100 < 68, 'order_completed',
            'payment_received'
        ) AS event_type,
        if(
            event_type = 'registration',
            CAST(NULL AS Nullable(String)),
            CAST(concat('BF-ORD-', toString(day_offset), '-', toString(event_no)) AS Nullable(String))
        ) AS order_id,
        if(
            event_type = 'registration',
            CAST(NULL AS Nullable(UInt32)),
            CAST(toUInt32(1 + (event_no % 50)) AS Nullable(UInt32))
        ) AS product_id,
        if(
            event_type = 'registration',
            CAST(NULL AS Nullable(Decimal(18,2))),
            CAST(
                toDecimal64(
                    40 + (day_offset % 7) * 12 + (event_no % 15) * 6 + (event_no % 100) / 100.0,
                    2
                ) AS Nullable(Decimal(18,2))
            )
        ) AS amount,
        toDateTime64(
            addSeconds(
                toDateTime(addDays(today(), -toInt32(45 - day_offset))),
                9 * 3600 + (event_no * 420) + ((day_offset * 29) % 300)
            ),
            3
        ) AS timestamp
    FROM
    (
        SELECT
            intDiv(number, 1500) AS day_offset,
            number % 1500 AS event_no
        FROM numbers(45 * 1500)
    )
) AS generated
WHERE generated.event_id NOT IN
(
    SELECT event_id
    FROM marketing.raw_backend_events
    WHERE startsWith(event_id, 'bfb_')
);
