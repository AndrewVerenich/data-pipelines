{{ config(
    materialized='table',
    alias='experiment_results',
    tags=['marts'],
    order_by='(experiment_key, metric_key)',
    engine='ReplacingMergeTree(computed_at)'
) }}

-- Wald z-test / CUPED / SRM and ship|stop|hold in ClickHouse SQL (replaces analysis-job).
WITH metrics AS (
    SELECT
        metric_key,
        metric_type,
        role,
        min_effect,
        toUInt8OrZero(toString(higher_is_better)) = 1
            OR lowerUTF8(toString(higher_is_better)) IN ('true', '1') AS higher_is_better
    FROM {{ ref('metric_config') }}
),
base AS (
    SELECT
        s.experiment_id AS experiment_id,
        s.experiment_key AS experiment_key,
        m.metric_key AS metric_key,
        m.role AS role,
        m.min_effect AS min_effect,
        m.higher_is_better AS higher_is_better,
        s.control_n AS control_n,
        s.treatment_n AS treatment_n,
        s.srm_p_value AS srm_p_value,
        s.cuped_theta AS cuped_theta,
        s.pooled_mean_pre AS pooled_mean_pre,
        multiIf(
            m.metric_key = 'conversion', s.control_conversions / nullIf(s.control_n, 0),
            m.metric_key = 'refund', s.control_refunds / nullIf(s.control_n, 0),
            s.control_revenue_sum / nullIf(s.control_n, 0)
        ) AS control_mean,
        multiIf(
            m.metric_key = 'conversion', s.treatment_conversions / nullIf(s.treatment_n, 0),
            m.metric_key = 'refund', s.treatment_refunds / nullIf(s.treatment_n, 0),
            s.treatment_revenue_sum / nullIf(s.treatment_n, 0)
        ) AS treatment_mean,
        multiIf(
            m.metric_key = 'conversion',
                (s.control_conversions / nullIf(s.control_n, 0))
                    * (1.0 - s.control_conversions / nullIf(s.control_n, 0)),
            m.metric_key = 'refund',
                (s.control_refunds / nullIf(s.control_n, 0))
                    * (1.0 - s.control_refunds / nullIf(s.control_n, 0)),
            greatest(
                (
                    s.control_revenue_sumsq
                    - pow(s.control_revenue_sum, 2) / nullIf(s.control_n, 0)
                ) / nullIf(s.control_n - 1, 0),
                0.0
            )
        ) AS control_var,
        multiIf(
            m.metric_key = 'conversion',
                (s.treatment_conversions / nullIf(s.treatment_n, 0))
                    * (1.0 - s.treatment_conversions / nullIf(s.treatment_n, 0)),
            m.metric_key = 'refund',
                (s.treatment_refunds / nullIf(s.treatment_n, 0))
                    * (1.0 - s.treatment_refunds / nullIf(s.treatment_n, 0)),
            greatest(
                (
                    s.treatment_revenue_sumsq
                    - pow(s.treatment_revenue_sum, 2) / nullIf(s.treatment_n, 0)
                ) / nullIf(s.treatment_n - 1, 0),
                0.0
            )
        ) AS treatment_var,
        -- CUPED adjusted means / vars for revenue only
        if(
            m.metric_key = 'revenue' AND s.cuped_theta IS NOT NULL,
            s.control_revenue_sum / nullIf(s.control_n, 0)
                - s.cuped_theta * (s.control_pre_sum / nullIf(s.control_n, 0) - s.pooled_mean_pre),
            CAST(NULL AS Nullable(Float64))
        ) AS cuped_control_mean,
        if(
            m.metric_key = 'revenue' AND s.cuped_theta IS NOT NULL,
            s.treatment_revenue_sum / nullIf(s.treatment_n, 0)
                - s.cuped_theta * (s.treatment_pre_sum / nullIf(s.treatment_n, 0) - s.pooled_mean_pre),
            CAST(NULL AS Nullable(Float64))
        ) AS cuped_treatment_mean,
        if(
            m.metric_key = 'revenue' AND s.cuped_theta IS NOT NULL AND s.control_n > 1,
            greatest(
                greatest(
                    (
                        s.control_revenue_sumsq
                        - pow(s.control_revenue_sum, 2) / s.control_n
                    ) / (s.control_n - 1),
                    0.0
                )
                + pow(s.cuped_theta, 2) * greatest(
                    (
                        s.control_pre_sumsq
                        - pow(s.control_pre_sum, 2) / s.control_n
                    ) / (s.control_n - 1),
                    0.0
                )
                - 2.0 * s.cuped_theta * (
                    (
                        s.control_revenue_pre_sum
                        - s.control_revenue_sum * s.control_pre_sum / s.control_n
                    ) / (s.control_n - 1)
                ),
                0.0
            ),
            CAST(NULL AS Nullable(Float64))
        ) AS cuped_control_var,
        if(
            m.metric_key = 'revenue' AND s.cuped_theta IS NOT NULL AND s.treatment_n > 1,
            greatest(
                greatest(
                    (
                        s.treatment_revenue_sumsq
                        - pow(s.treatment_revenue_sum, 2) / s.treatment_n
                    ) / (s.treatment_n - 1),
                    0.0
                )
                + pow(s.cuped_theta, 2) * greatest(
                    (
                        s.treatment_pre_sumsq
                        - pow(s.treatment_pre_sum, 2) / s.treatment_n
                    ) / (s.treatment_n - 1),
                    0.0
                )
                - 2.0 * s.cuped_theta * (
                    (
                        s.treatment_revenue_pre_sum
                        - s.treatment_revenue_sum * s.treatment_pre_sum / s.treatment_n
                    ) / (s.treatment_n - 1)
                ),
                0.0
            ),
            CAST(NULL AS Nullable(Float64))
        ) AS cuped_treatment_var,
        s.control_n AS c_n_raw,
        s.treatment_n AS t_n_raw
    FROM {{ ref('int_experiment_stats') }} AS s
    CROSS JOIN metrics AS m
),
effects AS (
    SELECT
        *,
        treatment_mean - control_mean AS lift,
        sqrt(control_var / nullIf(control_n, 0) + treatment_var / nullIf(treatment_n, 0)) AS se,
        if(
            cuped_control_mean IS NULL OR cuped_treatment_mean IS NULL,
            CAST(NULL AS Nullable(Float64)),
            cuped_treatment_mean - cuped_control_mean
        ) AS cuped_lift,
        if(
            cuped_control_var IS NULL OR cuped_treatment_var IS NULL,
            CAST(NULL AS Nullable(Float64)),
            sqrt(cuped_control_var / nullIf(control_n, 0) + cuped_treatment_var / nullIf(treatment_n, 0))
        ) AS cuped_se
    FROM base
),
wald AS (
    SELECT
        *,
        if(se < 1e-12, lift, lift - 1.959963984540054 * se) AS ci_low,
        if(se < 1e-12, lift, lift + 1.959963984540054 * se) AS ci_high,
        if(
            se < 1e-12,
            if(abs(lift) < 1e-12, 1.0, 0.0),
            greatest(0.0, least(1.0, erfc(abs(lift / se) / sqrt(2.0))))
        ) AS p_value,
        if(
            cuped_lift IS NULL OR cuped_se IS NULL,
            CAST(NULL AS Nullable(Float64)),
            if(cuped_se < 1e-12, cuped_lift, cuped_lift - 1.959963984540054 * cuped_se)
        ) AS cuped_ci_low,
        if(
            cuped_lift IS NULL OR cuped_se IS NULL,
            CAST(NULL AS Nullable(Float64)),
            if(cuped_se < 1e-12, cuped_lift, cuped_lift + 1.959963984540054 * cuped_se)
        ) AS cuped_ci_high,
        if(
            cuped_lift IS NULL OR cuped_se IS NULL,
            CAST(NULL AS Nullable(Float64)),
            if(
                cuped_se < 1e-12,
                if(abs(cuped_lift) < 1e-12, 1.0, 0.0),
                greatest(0.0, least(1.0, erfc(abs(cuped_lift / cuped_se) / sqrt(2.0))))
            )
        ) AS cuped_p_value
    FROM effects
),
flagged AS (
    SELECT
        *,
        if(
            higher_is_better,
            p_value < 0.05 AND lift >= min_effect,
            p_value < 0.05 AND lift <= -min_effect
        ) AS beneficial,
        if(
            higher_is_better,
            p_value < 0.05 AND lift <= -min_effect,
            p_value < 0.05 AND lift >= min_effect
        ) AS harmful
    FROM wald
),
decisions AS (
    SELECT
        experiment_key,
        multiIf(
            max(control_n) < 500 OR max(treatment_n) < 500 OR min(srm_p_value) < 0.001,
            'hold',
            max(if(role = 'guardrail' AND harmful, 1, 0)) = 1,
            'stop',
            max(if(role = 'primary' AND beneficial, 1, 0)) = 1,
            'ship',
            'hold'
        ) AS decision
    FROM flagged
    GROUP BY experiment_key
)
SELECT
    w.experiment_id AS experiment_id,
    w.experiment_key AS experiment_key,
    w.metric_key AS metric_key,
    w.role AS role,
    w.control_n AS control_n,
    w.treatment_n AS treatment_n,
    w.control_mean AS control_mean,
    w.treatment_mean AS treatment_mean,
    w.lift AS lift,
    w.ci_low AS ci_low,
    w.ci_high AS ci_high,
    w.p_value AS p_value,
    w.srm_p_value AS srm_p_value,
    w.cuped_lift AS cuped_lift,
    w.cuped_ci_low AS cuped_ci_low,
    w.cuped_ci_high AS cuped_ci_high,
    w.cuped_p_value AS cuped_p_value,
    d.decision AS decision,
    now64(3) AS computed_at
FROM flagged AS w
INNER JOIN decisions AS d ON w.experiment_key = d.experiment_key
