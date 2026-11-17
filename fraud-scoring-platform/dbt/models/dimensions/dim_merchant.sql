select
    {{ generate_surrogate_key(['merchant_id']) }} as merchant_sk,
    merchant_id,
    merchant_name,
    mcc,
    country,
    risk_score,
    created_at
from {{ ref('stg_merchants') }}
