{{ config(tags=['intermediate']) }}

WITH events AS (
    SELECT * FROM {{ ref('stg_commerce_events') }}
),

checkouts AS (
    SELECT
        user_id,
        argMax(email, event_time) AS email,
        argMax(cart_value, event_time) AS cart_value,
        max(event_time) AS checkout_at
    FROM events
    WHERE event_type = 'BEGIN_CHECKOUT'
    GROUP BY user_id
),

purchases AS (
    SELECT
        user_id,
        max(event_time) AS purchase_at
    FROM events
    WHERE event_type = 'PURCHASE'
    GROUP BY user_id
)

SELECT
    c.user_id,
    c.email,
    c.cart_value,
    c.checkout_at,
    p.purchase_at
FROM checkouts c
LEFT JOIN purchases p ON c.user_id = p.user_id
