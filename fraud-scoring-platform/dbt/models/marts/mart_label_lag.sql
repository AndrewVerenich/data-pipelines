select
    event_date,
    tx_cnt,
    labeled_cnt,
    tx_cnt - labeled_cnt as unlabeled_cnt,
    if(tx_cnt = 0, 0, labeled_cnt / tx_cnt) as label_coverage,
    avg_label_lag_hours,
    p50_label_lag_hours
from (
    select
        toDate(t.event_ts) as event_date,
        count() as tx_cnt,
        countIf(l.transaction_id != '') as labeled_cnt,
        avgIf(dateDiff('hour', t.event_ts, l.labeled_at), l.transaction_id != '') as avg_label_lag_hours,
        quantileIf(0.5)(dateDiff('hour', t.event_ts, l.labeled_at), l.transaction_id != '') as p50_label_lag_hours
    from {{ ref('fct_transactions') }} as t
    left join {{ ref('fct_fraud_labels') }} as l on t.transaction_id = l.transaction_id
    group by toDate(t.event_ts)
)
