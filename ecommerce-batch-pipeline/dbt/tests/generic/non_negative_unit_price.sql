{% test non_negative_unit_price(model) %}
select product_id, unit_price
from {{ model }}
where unit_price < 0
{% endtest %}
