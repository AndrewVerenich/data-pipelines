select
    toDate(created_at) as day,
    sum(amount_usd) as daily_revenue_usd,
    count() as transaction_count,
    avg(amount_usd) as avg_ticket_usd
from {{ ref('fct_transaction') }}
where transaction_status = 'SUCCESS'
group by 1
