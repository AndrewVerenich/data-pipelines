{% macro create_date_spine(start_date, end_date) -%}
select date_day
from {{ dbt_date.get_base_dates(start_date=start_date, end_date=end_date) }}
{%- endmacro %}
