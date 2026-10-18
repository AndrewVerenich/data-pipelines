-- Simple drift snapshot: current 24h feature means vs prior 7d baseline
with recent as (
    select
        avg(amount) as amount_mean,
        avg(user_tx_cnt_1h) as user_tx_cnt_1h_mean,
        avg(user_tx_cnt_24h) as user_tx_cnt_24h_mean,
        avg(amount_zscore_user) as amount_zscore_mean,
        avg(is_new_device_30d) as is_new_device_30d_mean,
        avg(is_foreign_ip) as is_foreign_ip_mean,
        avg(merchant_risk_score) as merchant_risk_mean,
        count() as row_cnt
    from {{ ref('feat_transaction_scoring') }}
    where event_ts >= now() - toIntervalHour(24)
),
baseline as (
    select
        avg(amount) as amount_mean,
        avg(user_tx_cnt_1h) as user_tx_cnt_1h_mean,
        avg(user_tx_cnt_24h) as user_tx_cnt_24h_mean,
        avg(amount_zscore_user) as amount_zscore_mean,
        avg(is_new_device_30d) as is_new_device_30d_mean,
        avg(is_foreign_ip) as is_foreign_ip_mean,
        avg(merchant_risk_score) as merchant_risk_mean,
        count() as row_cnt
    from {{ ref('feat_transaction_scoring') }}
    where event_ts >= now() - toIntervalDay(7)
      and event_ts < now() - toIntervalHour(24)
)
select
    now64(3) as computed_at,
    r.row_cnt as recent_rows,
    b.row_cnt as baseline_rows,
    r.amount_mean as recent_amount_mean,
    b.amount_mean as baseline_amount_mean,
    if(b.amount_mean = 0, 0, abs(r.amount_mean - b.amount_mean) / b.amount_mean) as amount_rel_shift,
    r.user_tx_cnt_1h_mean as recent_user_tx_cnt_1h_mean,
    b.user_tx_cnt_1h_mean as baseline_user_tx_cnt_1h_mean,
    if(b.user_tx_cnt_1h_mean = 0, 0, abs(r.user_tx_cnt_1h_mean - b.user_tx_cnt_1h_mean) / b.user_tx_cnt_1h_mean) as user_tx_cnt_1h_rel_shift,
    r.is_new_device_30d_mean as recent_new_device_rate,
    b.is_new_device_30d_mean as baseline_new_device_rate,
    r.is_foreign_ip_mean as recent_foreign_ip_rate,
    b.is_foreign_ip_mean as baseline_foreign_ip_rate,
    r.merchant_risk_mean as recent_merchant_risk_mean,
    b.merchant_risk_mean as baseline_merchant_risk_mean
from recent r
cross join baseline b
