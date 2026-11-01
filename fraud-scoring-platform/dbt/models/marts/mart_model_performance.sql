with scored as (
    select
        s.transaction_id as transaction_id,
        s.score as score,
        s.decision as decision,
        s.model_version as model_version,
        s.scored_at as scored_at,
        l.is_fraud as is_fraud,
        t.event_ts as event_ts
    from {{ ref('fct_fraud_scores') }} as s
    inner join {{ ref('fct_fraud_labels') }} as l on s.transaction_id = l.transaction_id
    inner join {{ ref('fct_transactions') }} as t on s.transaction_id = t.transaction_id
),
daily as (
    select
        toDate(event_ts) as event_date,
        model_version as model_version,
        count() as labeled_scored_cnt,
        avg(score) as avg_score,
        avg(is_fraud) as fraud_rate,
        countIf(score >= 0.7) as pred_pos_07,
        countIf(score >= 0.7 and is_fraud = 1) as tp_07,
        countIf(score >= 0.7 and is_fraud = 0) as fp_07,
        countIf(score < 0.7 and is_fraud = 1) as fn_07,
        countIf(score >= 0.9) as pred_pos_09,
        countIf(score >= 0.9 and is_fraud = 1) as tp_09,
        countIf(score >= 0.9 and is_fraud = 0) as fp_09,
        countIf(score < 0.9 and is_fraud = 1) as fn_09
    from scored
    group by
        toDate(event_ts),
        model_version
)
select
    event_date,
    model_version,
    labeled_scored_cnt,
    avg_score,
    fraud_rate,
    if(pred_pos_07 = 0, 0, tp_07 / pred_pos_07) as precision_at_0_7,
    if((tp_07 + fn_07) = 0, 0, tp_07 / (tp_07 + fn_07)) as recall_at_0_7,
    if(pred_pos_09 = 0, 0, tp_09 / pred_pos_09) as precision_at_0_9,
    if((tp_09 + fn_09) = 0, 0, tp_09 / (tp_09 + fn_09)) as recall_at_0_9,
    tp_07,
    fp_07,
    fn_07,
    tp_09,
    fp_09,
    fn_09
from daily
