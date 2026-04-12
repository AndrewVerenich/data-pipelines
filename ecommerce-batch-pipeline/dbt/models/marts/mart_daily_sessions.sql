{{
    config(
        materialized='table',
        tags=['marts']
    )
}}

select
    event_date,
    session_id,
    count(*) as event_count,
    uniqExact(user_id) as distinct_users,
    uniqExact(device) as distinct_devices,
    sumIf(1, level = 'ERROR') as error_events
from {{ ref('fct_events') }}
group by event_date, session_id
