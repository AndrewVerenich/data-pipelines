select
    transaction_id,
    event_ts,
    user_id,
    merchant_id,
    device_id,
    payment_method_id,
    amount,
    currency,
    channel,
    status,
    ip_country,
    is_3ds,
    card_bin,
    device_os,
    mcc,
    merchant_country,
    ingest_ts
from (
    select
        transaction_id,
        event_ts,
        user_id,
        merchant_id,
        device_id,
        payment_method_id,
        amount,
        currency,
        channel,
        status,
        ip_country,
        is_3ds,
        card_bin,
        device_os,
        mcc,
        merchant_country,
        ingest_ts,
        row_number() over (partition by transaction_id order by ingest_ts desc) as _rn
    from {{ source('raw_fraud', 'landing_transactions') }}
)
where _rn = 1
