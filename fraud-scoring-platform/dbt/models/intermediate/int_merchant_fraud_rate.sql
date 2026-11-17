select
    t.merchant_id,
    count() as merchant_tx_cnt,
    countIf(l.is_fraud = 1) as merchant_fraud_cnt,
    if(count() = 0, 0, countIf(l.is_fraud = 1) / count()) as merchant_fraud_rate
from {{ ref('stg_transactions') }} t
left join {{ ref('stg_labels') }} l on t.transaction_id = l.transaction_id
group by t.merchant_id
