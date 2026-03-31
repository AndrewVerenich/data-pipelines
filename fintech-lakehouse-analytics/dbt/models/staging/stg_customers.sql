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
    updated_at
from {{ source('raw_fintech', 'customers') }}
