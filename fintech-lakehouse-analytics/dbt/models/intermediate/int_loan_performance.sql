select
    l.loan_id,
    l.customer_id,
    l.account_id,
    l.loan_type,
    l.loan_status,
    l.principal_amount,
    l.outstanding_balance,
    count(lp.loan_payment_id) as payment_count,
    sum(lp.payment_amount) as total_paid,
    sum(case when lp.payment_status = 'LATE' then 1 else 0 end) as late_payment_count
from {{ ref('stg_loans') }} l
left join {{ ref('stg_loan_payments') }} lp on l.loan_id = lp.loan_id
group by 1,2,3,4,5,6,7
