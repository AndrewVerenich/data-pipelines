{{ config(
    materialized='view',
    tags=['marts', 'star', 'dimension']
) }}

-- Dimension: metric catalog (roles / thresholds for ship|stop|hold).
SELECT
    metric_key,
    metric_type,
    role,
    min_effect,
    toUInt8OrZero(toString(higher_is_better)) = 1
        OR lowerUTF8(toString(higher_is_better)) IN ('true', '1') AS higher_is_better
FROM {{ ref('metric_config') }}
