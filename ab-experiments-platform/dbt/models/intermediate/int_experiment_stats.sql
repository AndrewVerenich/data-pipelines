{{ config(materialized='view', tags=['intermediate']) }}

-- Pair control/treatment arms and compute Wald / SRM / CUPED inputs.
WITH arms AS (
    SELECT * FROM {{ ref('int_arm_stats') }}
),
paired AS (
    SELECT
        c.experiment_id AS experiment_id,
        c.experiment_key AS experiment_key,
        c.n AS control_n,
        t.n AS treatment_n,
        c.conversions AS control_conversions,
        t.conversions AS treatment_conversions,
        c.revenue_sum AS control_revenue_sum,
        t.revenue_sum AS treatment_revenue_sum,
        c.revenue_sumsq AS control_revenue_sumsq,
        t.revenue_sumsq AS treatment_revenue_sumsq,
        c.refunds AS control_refunds,
        t.refunds AS treatment_refunds,
        c.pre_sum AS control_pre_sum,
        t.pre_sum AS treatment_pre_sum,
        c.pre_sumsq AS control_pre_sumsq,
        t.pre_sumsq AS treatment_pre_sumsq,
        c.revenue_pre_sum AS control_revenue_pre_sum,
        t.revenue_pre_sum AS treatment_revenue_pre_sum
    FROM arms AS c
    INNER JOIN arms AS t
        ON c.experiment_key = t.experiment_key
       AND c.variant_key = 'control'
       AND t.variant_key = 'treatment'
),
srm AS (
    SELECT
        experiment_key,
        control_n,
        treatment_n,
        if(
            control_n + treatment_n = 0,
            0.0,
            (
                pow(control_n - (control_n + treatment_n) * 0.5, 2) / ((control_n + treatment_n) * 0.5)
                + pow(treatment_n - (control_n + treatment_n) * 0.5, 2) / ((control_n + treatment_n) * 0.5)
            )
        ) AS srm_chi
    FROM paired
)
SELECT
    p.*,
    s.srm_chi,
    -- χ²(1) survival ≈ erfc(sqrt(chi/2))
    greatest(0.0, least(1.0, erfc(sqrt(greatest(s.srm_chi, 0.0) / 2.0)))) AS srm_p_value,
    -- pooled CUPED theta for revenue
    if(
        (p.control_n + p.treatment_n) <= 2
            OR (
                (
                    (p.control_pre_sumsq + p.treatment_pre_sumsq)
                    - pow(p.control_pre_sum + p.treatment_pre_sum, 2) / (p.control_n + p.treatment_n)
                ) / (p.control_n + p.treatment_n - 1)
            ) < 1e-9,
        CAST(NULL AS Nullable(Float64)),
        (
            (
                (p.control_revenue_pre_sum + p.treatment_revenue_pre_sum)
                - (p.control_revenue_sum + p.treatment_revenue_sum)
                    * (p.control_pre_sum + p.treatment_pre_sum)
                    / (p.control_n + p.treatment_n)
            ) / (p.control_n + p.treatment_n - 1)
        )
        / (
            (
                (p.control_pre_sumsq + p.treatment_pre_sumsq)
                - pow(p.control_pre_sum + p.treatment_pre_sum, 2) / (p.control_n + p.treatment_n)
            ) / (p.control_n + p.treatment_n - 1)
        )
    ) AS cuped_theta,
    (p.control_pre_sum + p.treatment_pre_sum) / nullIf(p.control_n + p.treatment_n, 0) AS pooled_mean_pre
FROM paired AS p
INNER JOIN srm AS s ON p.experiment_key = s.experiment_key
