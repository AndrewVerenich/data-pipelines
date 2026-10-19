{{ config(tags=['staging']) }}

SELECT
    event_id,
    user_id,
    upper(event_type) AS event_type,
    email,
    product_id,
    cart_value,
    session_id,
    event_time
FROM {{ source('raw_retl', 'raw_commerce_events') }}
WHERE user_id != ''
  AND event_type != ''
