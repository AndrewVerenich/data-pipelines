select
    {{ generate_surrogate_key(['t.transaction_id']) }} as transaction_sk,
    t.transaction_id as transaction_id,
    t.customer_id as customer_id,
    dc.customer_sk as customer_sk,
    da.account_sk as account_sk,
    dm.merchant_sk as merchant_sk,
    dd.date_sk as date_sk,
    t.transaction_type as transaction_type,
    t.transaction_status as transaction_status,
    t.payment_channel as payment_channel,
    t.device_type as device_type,
    t.amount as amount,
    t.amount_usd as amount_usd,
    t.fee_amount as fee_amount,
    t.is_international as is_international,
    t.created_at as created_at
from {{ ref('int_transactions_enriched') }} t
left join {{ ref('dim_customer') }} dc on t.customer_id = dc.customer_id
left join {{ ref('dim_account') }} da on t.account_id = da.account_id
left join {{ ref('dim_merchant') }} dm on t.merchant_id = dm.merchant_id
left join {{ ref('dim_date') }} dd on toDate(t.created_at) = dd.date_day
