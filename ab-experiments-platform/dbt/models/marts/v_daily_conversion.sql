{{ config(materialized='view', tags=['marts', 'bi']) }}

SELECT
    toDate(exposed_at) AS day,
    experiment_key,
    variant_key,
    count() AS users,
    avg(converted) AS conversion_rate
FROM {{ source('ab', 'user_level') }}
GROUP BY day, experiment_key, variant_key
