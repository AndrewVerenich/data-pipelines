select
    {{ generate_surrogate_key(['customer_id']) }} as customer_sk,
    customer_id,
    full_name,
    email,
    country,
    city,
    kyc_status,
    risk_level,
    is_active,
    created_at
from {{ ref('stg_customers') }}
