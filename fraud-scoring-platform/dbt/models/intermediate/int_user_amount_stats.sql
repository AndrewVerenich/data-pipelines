-- Per-user historical amount stats (all history available in demo warehouse)
select
    user_id,
    avg(amount) as user_avg_amount,
    stddevPop(amount) as user_stddev_amount,
    quantile(0.95)(amount) as user_p95_amount,
    count() as user_tx_cnt_all
from {{ ref('stg_transactions') }}
group by user_id
