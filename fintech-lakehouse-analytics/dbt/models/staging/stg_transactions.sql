select
    id as transaction_id,
    customer_id,
    account_id,
    merchant_id,
    transaction_type,
    transaction_status,
    payment_channel,
    device_type,
    currency_code,
    exchange_rate,
    amount,
    amount_usd,
    fee_amount,
    is_international,
    created_at,
    updated_at
from {{ source('raw_fintech', 'transactions') }}
