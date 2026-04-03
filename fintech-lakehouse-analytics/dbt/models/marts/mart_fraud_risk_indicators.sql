select
    customer_id,
    toDate(created_at) as activity_day,
    count() as tx_count,
    sum(amount_usd) as total_amount_usd,
    sum(is_international) as intl_tx_count,
    countIf(transaction_status = 'FAILED') as failed_tx_count
from {{ ref('fct_transaction') }}
group by 1,2
having tx_count >= 5 or failed_tx_count >= 3 or intl_tx_count >= 2
