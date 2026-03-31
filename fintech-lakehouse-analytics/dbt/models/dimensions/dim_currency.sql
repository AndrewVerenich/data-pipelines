select
    {{ generate_surrogate_key(['currency_code']) }} as currency_sk,
    currency_code,
    currency_name,
    region
from {{ ref('currency_codes') }}
