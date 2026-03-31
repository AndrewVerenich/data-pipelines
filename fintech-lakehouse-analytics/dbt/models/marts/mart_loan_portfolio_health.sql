select
    loan_status,
    count() as loan_count,
    sum(outstanding_balance) as total_outstanding_balance,
    sumIf(outstanding_balance, loan_status in ('DEFAULTED', 'DELINQUENT')) as risky_outstanding_balance
from {{ ref('fct_loan') }}
group by 1
