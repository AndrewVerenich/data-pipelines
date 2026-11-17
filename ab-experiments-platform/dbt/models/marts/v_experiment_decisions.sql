{{ config(materialized='view', tags=['marts', 'bi']) }}

SELECT
    experiment_key,
    any(decision) AS decision,
    any(control_n) AS control_n,
    any(treatment_n) AS treatment_n,
    round(any(srm_p_value), 6) AS srm_p_value,
    round(any(lift), 6) AS conversion_lift,
    round(any(p_value), 6) AS conversion_p_value
FROM
(
    SELECT *
    FROM {{ ref('mart_experiment_results') }}
    WHERE metric_key = 'conversion'
    ORDER BY computed_at DESC
    LIMIT 1 BY experiment_key
)
GROUP BY experiment_key
