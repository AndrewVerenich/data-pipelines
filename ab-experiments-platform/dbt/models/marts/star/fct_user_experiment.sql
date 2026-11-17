{{ config(
    materialized='view',
    tags=['marts', 'star', 'fact']
) }}

-- Fact (user grain): sticky assignment = exposure unit with attributed outcomes.
-- Grain: (experiment_id, user_id)
SELECT
    experiment_id,
    experiment_key,
    variant_key,
    user_id,
    bucket,
    exposed_at,
    converted,
    revenue,
    refunded,
    pre_revenue
FROM {{ source('ab', 'user_level') }}
