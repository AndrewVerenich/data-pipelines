select
    id as merchant_id,
    merchant_name,
    merchant_category,
    mcc_code,
    country as merchant_country,
    city as merchant_city,
    is_online,
    created_at
from {{ source('raw_fintech', 'merchants') }}
