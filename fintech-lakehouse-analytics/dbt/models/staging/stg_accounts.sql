select
    account_id,
    customer_id,
    account_type,
    account_status,
    currency_code,
    balance,
    credit_limit,
    interest_rate,
    opened_at,
    updated_at
from (
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
        updated_at,
        row_number() over (partition by id order by updated_at desc, opened_at desc) as _cdc_rn
    from {{ source('raw_fintech', 'accounts') }}
)
where _cdc_rn = 1
