-- Point-in-time feature store table: one row per transaction.
-- Velocity windows use only prior events (t2.event_ts < t.event_ts).

{{ config(materialized='table') }}

with base as (
    select
        transaction_id,
        event_ts,
        user_id,
        merchant_id,
        device_id,
        payment_method_id,
        amount,
        status,
        mcc,
        channel,
        ip_country,
        merchant_country,
        device_os,
        hour_of_day,
        day_of_week,
        risk_tier,
        kyc_level,
        currency,
        is_foreign_ip,
        is_cross_border,
        is_3ds,
        merchant_risk_score
    from {{ ref('fct_transactions') }}
),
user_hist as (
    select
        t.transaction_id as transaction_id,
        countIf(t2.event_ts >= t.event_ts - toIntervalHour(1) and t2.event_ts < t.event_ts) as user_tx_cnt_1h,
        countIf(t2.event_ts >= t.event_ts - toIntervalHour(24) and t2.event_ts < t.event_ts) as user_tx_cnt_24h,
        countIf(t2.event_ts >= t.event_ts - toIntervalDay(7) and t2.event_ts < t.event_ts) as user_tx_cnt_7d,
        sumIf(t2.amount, t2.event_ts >= t.event_ts - toIntervalHour(24) and t2.event_ts < t.event_ts) as user_amount_sum_24h,
        uniqExactIf(t2.merchant_id, t2.event_ts >= t.event_ts - toIntervalDay(7) and t2.event_ts < t.event_ts) as user_unique_merchants_7d,
        uniqExactIf(t2.device_id, t2.event_ts >= t.event_ts - toIntervalDay(30) and t2.event_ts < t.event_ts) as user_unique_devices_30d,
        countIf(t2.status = 'declined' and t2.event_ts >= t.event_ts - toIntervalHour(24) and t2.event_ts < t.event_ts) as user_declines_24h,
        minIf(t2.event_ts, t2.event_ts < t.event_ts) as user_prev_tx_ts
    from base as t
    left join base as t2 on t.user_id = t2.user_id
    group by t.transaction_id
),
device_hist as (
    select
        t.transaction_id as transaction_id,
        countIf(t2.event_ts >= t.event_ts - toIntervalHour(1) and t2.event_ts < t.event_ts) as device_tx_cnt_1h,
        countIf(t2.event_ts < t.event_ts) as device_tx_cnt_before,
        minIf(t2.event_ts, t2.event_ts < t.event_ts) as device_first_seen_before
    from base as t
    left join base as t2 on t.device_id = t2.device_id
    group by t.transaction_id
),
merchant_hist as (
    select
        t.transaction_id as transaction_id,
        countIf(t2.event_ts >= t.event_ts - toIntervalHour(1) and t2.event_ts < t.event_ts) as merchant_tx_velocity_1h
    from base as t
    left join base as t2 on t.merchant_id = t2.merchant_id
    group by t.transaction_id
),
pm_hist as (
    select
        t.transaction_id as transaction_id,
        countIf(t2.event_ts < t.event_ts and t2.event_ts >= t.event_ts - toIntervalDay(7)) as pm_tx_cnt_7d,
        minIf(t2.event_ts, t2.event_ts < t.event_ts) as pm_first_seen_before
    from base as t
    left join base as t2 on t.payment_method_id = t2.payment_method_id
    group by t.transaction_id
)
select
    t.transaction_id as transaction_id,
    t.event_ts as event_ts,
    t.user_id as user_id,
    t.merchant_id as merchant_id,
    t.device_id as device_id,
    t.payment_method_id as payment_method_id,
    t.amount as amount,
    uh.user_tx_cnt_1h as user_tx_cnt_1h,
    uh.user_tx_cnt_24h as user_tx_cnt_24h,
    uh.user_tx_cnt_7d as user_tx_cnt_7d,
    uh.user_amount_sum_24h as user_amount_sum_24h,
    uh.user_unique_merchants_7d as user_unique_merchants_7d,
    uh.user_unique_devices_30d as user_unique_devices_30d,
    uh.user_declines_24h as user_declines_24h,
    if(uas.user_stddev_amount = 0, 0, (t.amount - uas.user_avg_amount) / uas.user_stddev_amount) as amount_zscore_user,
    if(uas.user_p95_amount = 0, 0, t.amount / uas.user_p95_amount) as amount_vs_user_p95,
    dh.device_tx_cnt_1h as device_tx_cnt_1h,
    if(dh.device_tx_cnt_before = 0, 1, 0) as is_new_device,
    if(dh.device_first_seen_before is null or dh.device_first_seen_before >= t.event_ts - toIntervalDay(30), 1, 0) as is_new_device_30d,
    mh.merchant_tx_velocity_1h as merchant_tx_velocity_1h,
    t.merchant_risk_score as merchant_risk_score,
    if(ph.pm_first_seen_before is null or ph.pm_first_seen_before >= t.event_ts - toIntervalDay(7), 1, 0) as is_new_pm_7d,
    ph.pm_tx_cnt_7d as pm_tx_cnt_7d,
    t.is_foreign_ip as is_foreign_ip,
    t.is_cross_border as is_cross_border,
    t.is_3ds as is_3ds,
    if(uh.user_prev_tx_ts is null, 999999, dateDiff('second', uh.user_prev_tx_ts, t.event_ts)) as seconds_since_prev_tx,
    t.mcc as cat_mcc,
    t.channel as cat_channel,
    t.ip_country as cat_ip_country,
    t.merchant_country as cat_merchant_country,
    t.device_os as cat_device_os,
    cast(t.hour_of_day as String) as cat_hour_of_day,
    cast(t.day_of_week as String) as cat_day_of_week,
    t.risk_tier as cat_risk_tier,
    t.kyc_level as cat_kyc_level,
    t.currency as cat_currency,
    l.is_fraud as label_is_fraud,
    l.label_source as label_source,
    l.fraud_pattern as fraud_pattern,
    l.labeled_at as labeled_at
from base as t
left join user_hist as uh on t.transaction_id = uh.transaction_id
left join device_hist as dh on t.transaction_id = dh.transaction_id
left join merchant_hist as mh on t.transaction_id = mh.transaction_id
left join pm_hist as ph on t.transaction_id = ph.transaction_id
left join {{ ref('int_user_amount_stats') }} as uas on t.user_id = uas.user_id
left join {{ ref('fct_fraud_labels') }} as l on t.transaction_id = l.transaction_id
