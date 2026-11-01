select
    'merchant' as segment_type,
    t.merchant_id as segment_id,
    any(t.mcc) as mcc,
    count() as tx_cnt,
    avg(s.score) as avg_score,
    countIf(l.is_fraud = 1) as fraud_cnt,
    sum(t.amount) as amount_sum
from {{ ref('fct_transactions') }} as t
left join {{ ref('fct_fraud_labels') }} as l on t.transaction_id = l.transaction_id
left join (
    select
        transaction_id as transaction_id,
        argMax(score, scored_at) as score
    from {{ ref('fct_fraud_scores') }}
    group by transaction_id
) as s on t.transaction_id = s.transaction_id
group by t.merchant_id
having avg_score > 0.5 or fraud_cnt >= 3

union all

select
    'user' as segment_type,
    t.user_id as segment_id,
    '' as mcc,
    count() as tx_cnt,
    avg(s.score) as avg_score,
    countIf(l.is_fraud = 1) as fraud_cnt,
    sum(t.amount) as amount_sum
from {{ ref('fct_transactions') }} as t
left join {{ ref('fct_fraud_labels') }} as l on t.transaction_id = l.transaction_id
left join (
    select
        transaction_id as transaction_id,
        argMax(score, scored_at) as score
    from {{ ref('fct_fraud_scores') }}
    group by transaction_id
) as s on t.transaction_id = s.transaction_id
group by t.user_id
having avg_score > 0.6 or fraud_cnt >= 2
