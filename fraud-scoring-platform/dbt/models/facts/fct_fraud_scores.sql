select
    {{ generate_surrogate_key(['transaction_id', 'model_version', 'scored_at']) }} as score_sk,
    transaction_id,
    score,
    decision,
    model_version,
    scored_at,
    top_features
from {{ ref('stg_scores') }}
