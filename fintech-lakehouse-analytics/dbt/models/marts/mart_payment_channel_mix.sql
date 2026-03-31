select
    payment_channel,
    count() as transaction_count,
    sum(amount_usd) as amount_usd
from {{ ref('fct_transaction') }}
where transaction_status = 'SUCCESS'
group by 1
