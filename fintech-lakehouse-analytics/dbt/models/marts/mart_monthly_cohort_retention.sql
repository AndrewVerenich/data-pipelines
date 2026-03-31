with cohort as (
    select
        customer_sk,
        toStartOfMonth(min(created_at)) as cohort_month
    from {{ ref('fct_transaction') }}
    group by 1
),
activity as (
    select
        customer_sk,
        toStartOfMonth(created_at) as active_month
    from {{ ref('fct_transaction') }}
    group by 1, 2
)
select
    c.cohort_month,
    a.active_month,
    dateDiff('month', c.cohort_month, a.active_month) as cohort_age_month,
    count() as active_customers
from cohort c
join activity a on c.customer_sk = a.customer_sk
group by 1,2,3
