select
    id as account_id,
    customer_id,
    account_type,
    account_status,
    currency_code,
    balance,
    credit_limit,
    interest_rate,
    opened_at,
    updated_at
from {{ source('raw_fintech', 'accounts') }}
