{{ config(materialized='view', tags=['intermediate']) }}

-- Per-experiment arm aggregates from user_level (assignment = exposure unit).
SELECT
    experiment_id,
    experiment_key,
    variant_key,
    count() AS n,
    sum(converted) AS conversions,
    sum(revenue) AS revenue_sum,
    sum(revenue * revenue) AS revenue_sumsq,
    sum(refunded) AS refunds,
    sum(pre_revenue) AS pre_sum,
    sum(pre_revenue * pre_revenue) AS pre_sumsq,
    sum(revenue * pre_revenue) AS revenue_pre_sum
FROM {{ source('ab', 'user_level') }}
GROUP BY
    experiment_id,
    experiment_key,
    variant_key
