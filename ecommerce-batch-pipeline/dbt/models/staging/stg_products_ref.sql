select
    ingest_batch_id,
    product_id,
    product_name,
    category,
    unit_price
from {{ source('raw_ecommerce', 'raw_ref_products') }}
