with base as (
    select
        customer_sk,
        dateDiff('day', max(toDate(created_at)), today()) as recency_days,
        count() as frequency,
        sum(amount_usd) as monetary_value
    from {{ ref('fct_transaction') }}
    where transaction_status = 'SUCCESS'
    group by 1
)
select
    *,
    case
        when monetary_value > 150000 then 'VIP'
        when monetary_value > 50000 then 'LOYAL'
        else 'REGULAR'
    end as segment
from base
