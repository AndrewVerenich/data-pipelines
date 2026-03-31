select
    customer_sk,
    sum(amount_usd) as lifetime_value_usd,
    count() as transaction_count
from {{ ref('fct_transaction') }}
where transaction_status = 'SUCCESS'
group by 1
