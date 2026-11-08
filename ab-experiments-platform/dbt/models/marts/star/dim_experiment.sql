{{ config(
    materialized='view',
    tags=['marts', 'star', 'dimension']
) }}

-- Dimension: one row per experiment observed in the analysis unit.
SELECT
    experiment_id,
    experiment_key,
    min(exposed_at) AS first_exposed_at,
    max(exposed_at) AS last_exposed_at,
    count() AS users_n
FROM {{ source('ab', 'user_level') }}
GROUP BY
    experiment_id,
    experiment_key
