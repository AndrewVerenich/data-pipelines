{{ config(
    materialized='view',
    tags=['marts', 'star', 'dimension']
) }}

-- Dimension: experiment arms (control / treatment).
SELECT
    variant_key,
    if(variant_key = 'control', 1, 0) AS is_control,
    if(variant_key = 'treatment', 1, 0) AS is_treatment
FROM
(
    SELECT DISTINCT variant_key
    FROM {{ source('ab', 'user_level') }}
)
