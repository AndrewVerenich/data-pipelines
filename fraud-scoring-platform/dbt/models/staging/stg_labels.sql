select
    transaction_id,
    is_fraud,
    label_source,
    fraud_pattern,
    labeled_at,
    ingest_ts
from (
    select
        transaction_id,
        is_fraud,
        label_source,
        fraud_pattern,
        labeled_at,
        ingest_ts,
        row_number() over (partition by transaction_id order by labeled_at desc) as _rn
    from {{ source('raw_fraud', 'landing_labels') }}
)
where _rn = 1
