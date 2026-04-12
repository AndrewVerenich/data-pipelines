select
    ingest_batch_id,
    user_id,
    email,
    country,
    cohort
from {{ source('raw_ecommerce', 'raw_ref_users') }}
