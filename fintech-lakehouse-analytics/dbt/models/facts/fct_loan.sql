select
    {{ generate_surrogate_key(['loan_id']) }} as loan_sk,
    loan_id,
    customer_id,
    account_id,
    loan_type,
    loan_status,
    principal_amount,
    outstanding_balance,
    payment_count,
    total_paid,
    late_payment_count
from {{ ref('int_loan_performance') }}
