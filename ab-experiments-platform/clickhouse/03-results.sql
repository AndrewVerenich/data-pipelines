CREATE TABLE IF NOT EXISTS ab.experiment_results (
    experiment_id    UInt32,
    experiment_key   LowCardinality(String),
    metric_key       LowCardinality(String),
    role             LowCardinality(String),
    control_n        UInt64,
    treatment_n      UInt64,
    control_mean     Float64,
    treatment_mean   Float64,
    lift             Float64,
    ci_low           Float64,
    ci_high          Float64,
    p_value          Float64,
    srm_p_value      Float64,
    cuped_lift       Nullable(Float64),
    cuped_ci_low     Nullable(Float64),
    cuped_ci_high    Nullable(Float64),
    cuped_p_value    Nullable(Float64),
    decision         LowCardinality(String),
    computed_at      DateTime64(3)
) ENGINE = ReplacingMergeTree(computed_at)
ORDER BY (experiment_key, metric_key);
