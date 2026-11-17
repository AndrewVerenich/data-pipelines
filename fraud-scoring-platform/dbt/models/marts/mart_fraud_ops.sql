select
    toDate(t.event_ts) as event_date,
    t.channel as channel,
    t.mcc as mcc,
    count() as tx_cnt,
    countIf(l.is_fraud = 1) as fraud_cnt,
    if(count() = 0, 0, countIf(l.is_fraud = 1) / count()) as fraud_rate,
    sum(t.amount) as amount_sum,
    sumIf(t.amount, l.is_fraud = 1) as fraud_amount_sum,
    countIf(s.decision = 'BLOCK') as block_cnt,
    countIf(s.decision = 'CHALLENGE') as challenge_cnt,
    countIf(s.decision = 'ALLOW') as allow_cnt,
    avg(s.score) as avg_score
from {{ ref('fct_transactions') }} as t
left join {{ ref('fct_fraud_labels') }} as l on t.transaction_id = l.transaction_id
left join (
    select
        transaction_id as transaction_id,
        argMax(score, scored_at) as score,
        argMax(decision, scored_at) as decision
    from {{ ref('fct_fraud_scores') }}
    group by transaction_id
) as s on t.transaction_id = s.transaction_id
group by
    toDate(t.event_ts),
    t.channel,
    t.mcc
