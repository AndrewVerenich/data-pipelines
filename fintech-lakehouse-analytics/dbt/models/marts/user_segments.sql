{{ config(materialized='table') }}

SELECT
    user_id,
    SUM(amount) AS total_spent,
    CASE
        WHEN SUM(amount) > 10000 THEN 'VIP'
        ELSE 'REGULAR'
        END AS segment
FROM {{ ref('transactions_stg') }}
GROUP BY user_id
