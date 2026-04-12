{{
    config(
        materialized='table',
        tags=['facts']
    )
}}

with events_sk as (
    select
        *,
        {{ dbt_utils.generate_surrogate_key(
            ['user_id', 'session_id', 'event', "toString(event_ts)"]
        ) }} as event_sk
    from {{ ref('stg_events') }}
)
select
    e.event_sk as event_sk,
    e.ingest_batch_id as ingest_batch_id,
    e.event_date as event_date,
    e.event_ts as event_ts,
    e.minute as minute,
    e.level as level,
    e.event as event,
    e.user_id as user_id,
    e.session_id as session_id,
    e.device as device,
    e.page as page,
    e.error_type as error_type,
    e.payment_method as payment_method,
    e.category as category,
    e.product_id as product_id,
    e.order_id as order_id,
    e.user_country as user_country,
    e.user_email as user_email,
    e.product_name as product_name,
    e.product_category_ref as product_category_ref,
    e.unit_price as unit_price,
    u.user_sk as user_sk,
    p.product_sk as product_sk
from events_sk as e
inner join {{ ref('dim_user') }} as u on e.user_id = u.user_id
left join {{ ref('dim_product') }} as p on e.product_id = p.product_id
