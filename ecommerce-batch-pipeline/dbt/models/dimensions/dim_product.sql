{{
    config(
        materialized='table',
        tags=['dimensions']
    )
}}

select
    {{ dbt_utils.generate_surrogate_key(['product_id']) }} as product_sk,
    product_id,
    product_name,
    category,
    unit_price,
    toDateTime('1970-01-01 00:00:00') as valid_from,
    toDateTime('2099-12-31 23:59:59') as valid_to,
    toUInt8(1) as is_current
from {{ ref('int_products_latest') }}
