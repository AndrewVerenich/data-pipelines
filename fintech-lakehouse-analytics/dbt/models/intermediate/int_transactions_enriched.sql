select
    t.transaction_id as transaction_id,
    t.customer_id as customer_id,
    t.account_id as account_id,
    t.merchant_id as merchant_id,
    t.transaction_type as transaction_type,
    t.transaction_status as transaction_status,
    t.payment_channel as payment_channel,
    t.device_type as device_type,
    t.currency_code as currency_code,
    t.amount as amount,
    t.amount_usd as amount_usd,
    t.fee_amount as fee_amount,
    t.is_international as is_international,
    t.created_at as created_at,
    a.account_type as account_type,
    a.account_status as account_status,
    c.country as customer_country,
    c.risk_level as customer_risk_level,
    m.merchant_category as merchant_category
from {{ ref('stg_transactions') }} t
left join {{ ref('stg_accounts') }} a on t.account_id = a.account_id
left join {{ ref('stg_customers') }} c on t.customer_id = c.customer_id
left join {{ ref('stg_merchants') }} m on t.merchant_id = m.merchant_id
