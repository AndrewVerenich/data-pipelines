select
    id as exchange_rate_id,
    base_currency,
    target_currency,
    rate,
    effective_date,
    created_at
from {{ source('raw_fintech', 'exchange_rates') }}
