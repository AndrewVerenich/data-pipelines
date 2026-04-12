{{
    config(
        materialized='table',
        tags=['marts']
    )
}}

select
    event_date,
    device,
    event,
    count(*) as event_count,
    uniqExact(session_id) as distinct_sessions
from {{ ref('fct_events') }}
group by event_date, device, event
