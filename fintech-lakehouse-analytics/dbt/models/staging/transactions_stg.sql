{{ config(materialized='view') }}

SELECT
    id,
    user_id,
    amount,
    status,
    created_at
FROM transactions
WHERE status IS NOT NULL