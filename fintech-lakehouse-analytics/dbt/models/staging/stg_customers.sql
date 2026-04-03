select
    customer_id,
    full_name,
    email,
    phone,
    country,
    city,
    date_of_birth,
    kyc_status,
    risk_level,
    is_active,
    created_at,
    updated_at
from (
    select
        id as customer_id,
        full_name,
        email,
        phone,
        country,
        city,
        date_of_birth,
        kyc_status,
        risk_level,
        is_active,
        created_at,
        updated_at,
        row_number() over (partition by id order by updated_at desc, created_at desc) as _cdc_rn
    from {{ source('raw_fintech', 'customers') }}
)
where _cdc_rn = 1
