select
    transaction_id,
    score,
    decision,
    model_version,
    scored_at,
    top_features,
    ingest_ts
from (
    select
        transaction_id,
        score,
        decision,
        model_version,
        scored_at,
        top_features,
        ingest_ts,
        row_number() over (
            partition by transaction_id, model_version
            order by scored_at desc
        ) as _rn
    from {{ source('raw_fraud', 'landing_scores') }}
)
where _rn = 1
