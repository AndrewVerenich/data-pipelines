select
    {{ generate_surrogate_key(['transaction_id']) }} as label_sk,
    transaction_id,
    is_fraud,
    label_source,
    fraud_pattern,
    labeled_at
from {{ ref('stg_labels') }}
