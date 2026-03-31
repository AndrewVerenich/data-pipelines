select
    c.customer_id,
    count(distinct a.account_id) as account_count,
    sum(a.balance) as total_balance,
    avg(a.interest_rate) as avg_interest_rate
from {{ ref('stg_customers') }} c
left join {{ ref('stg_accounts') }} a on c.customer_id = a.customer_id
group by 1
