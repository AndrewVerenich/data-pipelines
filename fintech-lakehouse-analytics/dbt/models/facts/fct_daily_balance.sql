select
    toDate(a.updated_at) as balance_day,
    a.account_id,
    a.customer_id,
    any(a.currency_code) as currency_code,
    avg(a.balance) as avg_balance,
    max(a.balance) as max_balance
from {{ ref('stg_accounts') }} a
group by 1, 2, 3
