select
    {{ generate_surrogate_key(['account_id']) }} as account_sk,
    account_id,
    customer_id,
    account_type,
    account_status,
    currency_code,
    opened_at
from {{ ref('stg_accounts') }}
