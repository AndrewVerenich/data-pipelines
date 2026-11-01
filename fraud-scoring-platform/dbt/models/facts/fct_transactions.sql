select
    {{ generate_surrogate_key(['transaction_id']) }} as transaction_sk,
    t.transaction_id as transaction_id,
    t.event_ts as event_ts,
    toYYYYMMDD(toDate(t.event_ts)) as date_sk,
    u.user_sk as user_sk,
    m.merchant_sk as merchant_sk,
    d.device_sk as device_sk,
    pm.payment_method_sk as payment_method_sk,
    t.user_id as user_id,
    t.merchant_id as merchant_id,
    t.device_id as device_id,
    t.payment_method_id as payment_method_id,
    t.amount as amount,
    t.currency as currency,
    t.channel as channel,
    t.status as status,
    t.ip_country as ip_country,
    t.is_3ds as is_3ds,
    t.card_bin as card_bin,
    t.device_os as device_os,
    t.mcc as mcc,
    t.merchant_country as merchant_country,
    t.is_foreign_ip as is_foreign_ip,
    t.is_cross_border as is_cross_border,
    t.hour_of_day as hour_of_day,
    t.day_of_week as day_of_week,
    t.kyc_level as kyc_level,
    t.risk_tier as risk_tier,
    t.merchant_risk_score as merchant_risk_score
from {{ ref('int_transactions_enriched') }} as t
left join {{ ref('dim_user') }} as u
    on t.user_id = u.user_id and u.is_current = 1
left join {{ ref('dim_merchant') }} as m
    on t.merchant_id = m.merchant_id
left join {{ ref('dim_device') }} as d
    on t.device_id = d.device_id
left join {{ ref('dim_payment_method') }} as pm
    on t.payment_method_id = pm.payment_method_id
