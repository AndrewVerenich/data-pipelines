select
    {{ generate_surrogate_key(['merchant_id']) }} as merchant_sk,
    merchant_id,
    merchant_name,
    merchant_category,
    mcc_code,
    merchant_country,
    merchant_city,
    is_online
from {{ ref('stg_merchants') }}
