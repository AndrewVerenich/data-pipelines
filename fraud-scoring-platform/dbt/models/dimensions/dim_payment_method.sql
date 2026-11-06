select
    {{ generate_surrogate_key(['payment_method_id']) }} as payment_method_sk,
    payment_method_id,
    any(card_bin) as card_bin,
    any(user_id) as primary_user_id,
    min(event_ts) as first_seen_at,
    max(event_ts) as last_seen_at,
    count() as tx_cnt
from {{ ref('stg_transactions') }}
group by payment_method_id
