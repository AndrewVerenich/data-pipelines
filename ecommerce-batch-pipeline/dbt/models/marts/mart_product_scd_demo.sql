{{
    config(
        materialized='table',
        tags=['marts']
    )
}}

select
    toUInt64(product_sk) as product_sk,
    product_id,
    product_name,
    category,
    toFloat64(unit_price) as unit_price,
    parseDateTimeBestEffort(toString(valid_from)) as valid_from,
    parseDateTimeBestEffort(toString(valid_to)) as valid_to,
    toUInt8(is_current) as is_current
from {{ ref('seed_product_scd') }}
