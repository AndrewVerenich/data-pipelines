{{ config(materialized='view', tags=['marts', 'bi']) }}

SELECT
    experiment_key,
    metric_key,
    any(role) AS role,
    round(any(control_mean), 6) AS control_mean,
    round(any(treatment_mean), 6) AS treatment_mean,
    round(any(lift), 6) AS lift,
    round(any(ci_low), 6) AS ci_low,
    round(any(ci_high), 6) AS ci_high,
    round(any(p_value), 6) AS p_value,
    round(any(cuped_lift), 6) AS cuped_lift,
    round(any(cuped_p_value), 6) AS cuped_p_value,
    any(decision) AS decision
FROM
(
    SELECT *
    FROM {{ ref('mart_experiment_results') }}
    ORDER BY computed_at DESC
    LIMIT 1 BY experiment_key, metric_key
)
GROUP BY experiment_key, metric_key
