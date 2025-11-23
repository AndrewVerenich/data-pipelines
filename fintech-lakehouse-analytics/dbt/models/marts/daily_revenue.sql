{{ config(materialized='table') }}

SELECT
    toDate(created_at) AS day,
    SUM(amount) AS daily_revenue
FROM {{ ref('transactions_stg') }}
GROUP BY day
ORDER BY day
