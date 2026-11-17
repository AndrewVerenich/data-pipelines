{{ config(materialized='table') }}

with bounds as (
    select
        toDate(min(event_ts)) as min_d,
        toDate(max(event_ts)) as max_d
    from {{ ref('stg_transactions') }}
),
spine as (
    select
        addDays(min_d, number) as date_day
    from bounds
    array join range(toUInt32(dateDiff('day', min_d, max_d) + 1)) as number
)
select
    toYYYYMMDD(date_day) as date_sk,
    date_day as date_day,
    toYear(date_day) as year,
    toMonth(date_day) as month,
    toDayOfMonth(date_day) as day_of_month,
    toDayOfWeek(date_day) as day_of_week,
    toISOWeek(date_day) as iso_week,
    if(toDayOfWeek(date_day) in (6, 7), 1, 0) as is_weekend
from spine
