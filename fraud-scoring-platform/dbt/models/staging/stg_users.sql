select
    user_id,
    email,
    country,
    kyc_level,
    risk_tier,
    created_at,
    updated_at,
    ingest_ts
from (
    select
        user_id,
        email,
        country,
        kyc_level,
        risk_tier,
        created_at,
        updated_at,
        ingest_ts,
        row_number() over (partition by user_id order by updated_at desc) as _rn
    from {{ source('raw_fraud', 'landing_users') }}
)
where _rn = 1
