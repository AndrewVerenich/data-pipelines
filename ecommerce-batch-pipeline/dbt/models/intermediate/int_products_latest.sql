select
    product_id,
    product_name,
    category,
    unit_price,
    ingest_batch_id
from (
    select
        *,
        row_number() over (partition by product_id order by ingest_batch_id desc) as rn
    from {{ ref('stg_products_ref') }}
)
where rn = 1
