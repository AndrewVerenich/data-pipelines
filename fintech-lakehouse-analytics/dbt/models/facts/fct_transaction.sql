select
    {{ generate_surrogate_key(['t.transaction_id']) }} as transaction_sk,
    t.transaction_id,
    dc.customer_sk,
    da.account_sk,
    dm.merchant_sk,
    dd.date_sk,
    t.transaction_type,
    t.transaction_status,
    t.payment_channel,
    t.device_type,
    t.amount,
    t.amount_usd,
    t.fee_amount,
    t.is_international,
    t.created_at
from {{ ref('int_transactions_enriched') }} t
left join {{ ref('dim_customer') }} dc on t.customer_id = dc.customer_id
left join {{ ref('dim_account') }} da on t.account_id = da.account_id
left join {{ ref('dim_merchant') }} dm on t.merchant_id = dm.merchant_id
left join {{ ref('dim_date') }} dd on toDate(t.created_at) = dd.date_day
