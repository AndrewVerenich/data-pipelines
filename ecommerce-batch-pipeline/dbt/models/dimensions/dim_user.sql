{{
    config(
        materialized='table',
        tags=['dimensions']
    )
}}

select
    {{ dbt_utils.generate_surrogate_key(['user_id']) }} as user_sk,
    user_id,
    email,
    country,
    cohort,
    ingest_batch_id as as_of_batch_id
from {{ ref('int_users_latest') }}
