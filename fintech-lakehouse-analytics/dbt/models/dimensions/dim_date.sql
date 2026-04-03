select
    {{ generate_surrogate_key(['date_day']) }} as date_sk,
    date_day,
    year_num,
    quarter_num,
    month_num,
    day_of_month,
    is_weekend
from {{ ref('seed_dim_date') }}
