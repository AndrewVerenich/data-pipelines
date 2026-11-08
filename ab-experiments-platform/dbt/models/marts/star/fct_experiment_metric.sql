{{ config(
    materialized='view',
    tags=['marts', 'star', 'fact']
) }}

-- Fact (experiment × metric grain): Wald / CUPED estimates and decision.
-- Grain: (experiment_key, metric_key); latest row wins via ReplacingMergeTree upstream.
SELECT
    experiment_id,
    experiment_key,
    metric_key,
    role,
    control_n,
    treatment_n,
    control_mean,
    treatment_mean,
    lift,
    ci_low,
    ci_high,
    p_value,
    srm_p_value,
    cuped_lift,
    cuped_ci_low,
    cuped_ci_high,
    cuped_p_value,
    decision,
    computed_at
FROM {{ ref('mart_experiment_results') }}
