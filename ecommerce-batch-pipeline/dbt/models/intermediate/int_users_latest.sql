select
    user_id,
    email,
    country,
    cohort,
    ingest_batch_id
from (
    select
        *,
        row_number() over (partition by user_id order by ingest_batch_id desc) as rn
    from {{ ref('stg_users') }}
)
where rn = 1
