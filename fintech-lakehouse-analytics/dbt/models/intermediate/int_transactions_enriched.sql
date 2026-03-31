select
    t.transaction_id,
    t.customer_id,
    t.account_id,
    t.merchant_id,
    t.transaction_type,
    t.transaction_status,
    t.payment_channel,
    t.device_type,
    t.currency_code,
    t.amount,
    t.amount_usd,
    t.fee_amount,
    t.is_international,
    t.created_at,
    a.account_type,
    a.account_status,
    c.country as customer_country,
    c.risk_level as customer_risk_level,
    m.merchant_category
from {{ ref('stg_transactions') }} t
left join {{ ref('stg_accounts') }} a on t.account_id = a.account_id
left join {{ ref('stg_customers') }} c on t.customer_id = c.customer_id
left join {{ ref('stg_merchants') }} m on t.merchant_id = m.merchant_id
