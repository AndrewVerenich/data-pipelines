select
    merchant_id,
    merchant_name,
    merchant_category,
    mcc_code,
    merchant_country,
    merchant_city,
    is_online,
    created_at
from (
    select
        id as merchant_id,
        merchant_name,
        merchant_category,
        mcc_code,
        country as merchant_country,
        city as merchant_city,
        is_online,
        created_at,
        row_number() over (partition by id order by created_at desc) as _cdc_rn
    from {{ source('raw_fintech', 'merchants') }}
)
where _cdc_rn = 1
