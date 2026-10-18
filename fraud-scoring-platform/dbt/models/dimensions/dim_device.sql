select
    {{ generate_surrogate_key(['device_id']) }} as device_sk,
    device_id,
    any(device_os) as device_os,
    any(user_id) as primary_user_id,
    min(event_ts) as first_seen_at,
    max(event_ts) as last_seen_at,
    count() as tx_cnt
from {{ ref('stg_transactions') }}
group by device_id
