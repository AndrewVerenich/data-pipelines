select
    merchant_id,
    merchant_name,
    mcc,
    country,
    risk_score,
    created_at,
    ingest_ts
from (
    select
        merchant_id,
        merchant_name,
        mcc,
        country,
        risk_score,
        created_at,
        ingest_ts,
        row_number() over (partition by merchant_id order by created_at desc) as _rn
    from {{ source('raw_fraud', 'landing_merchants') }}
)
where _rn = 1
