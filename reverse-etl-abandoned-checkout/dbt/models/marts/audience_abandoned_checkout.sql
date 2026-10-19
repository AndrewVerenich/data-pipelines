{{ config(
    tags=['marts'],
    materialized='table',
    order_by='user_id',
    engine='MergeTree()',
) }}

-- Desired membership: began checkout, no later purchase, still inside the abandon window,
-- and past a short grace period so we do not email mid-checkout.
SELECT
    user_id,
    email,
    cart_value,
    lower(hex(sipHash64(user_id, email, toString(cart_value), toString(checkout_at)))) AS version,
    checkout_at,
    now64(3) AS computed_at
FROM {{ ref('int_user_checkout_profile') }}
WHERE checkout_at >= now() - INTERVAL 24 HOUR
  AND checkout_at <= now() - INTERVAL 2 MINUTE
  AND (purchase_at IS NULL OR purchase_at < checkout_at)
