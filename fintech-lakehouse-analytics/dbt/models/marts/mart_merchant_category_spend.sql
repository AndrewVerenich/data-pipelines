select
    coalesce(dm.merchant_category, 'UNMAPPED') as merchant_category,
    count() as transaction_count,
    sum(ft.amount_usd) as total_spend_usd
from {{ ref('fct_transaction') }} ft
left join {{ ref('dim_merchant') }} dm on ft.merchant_sk = dm.merchant_sk
where ft.transaction_status = 'SUCCESS'
group by 1
