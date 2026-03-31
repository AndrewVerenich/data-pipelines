select
    id as loan_id,
    customer_id,
    account_id,
    loan_type,
    principal_amount,
    interest_rate,
    term_months,
    monthly_payment,
    outstanding_balance,
    loan_status,
    issued_at,
    maturity_at,
    updated_at
from {{ source('raw_fintech', 'loans') }}
