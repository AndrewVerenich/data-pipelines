select
    id as loan_payment_id,
    loan_id,
    payment_amount,
    principal_portion,
    interest_portion,
    payment_status,
    payment_channel,
    payment_date,
    created_at
from {{ source('raw_fintech', 'loan_payments') }}
