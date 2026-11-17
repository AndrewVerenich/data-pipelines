{% macro drop_clickhouse_dbt_backups() %}
  {% if execute %}
    {% set find_sql %}
      SELECT name
      FROM system.tables
      WHERE database = '{{ target.schema }}'
        AND name LIKE '%__dbt_backup%'
    {% endset %}
    {% set res = run_query(find_sql) %}
    {% if res and res.rows and (res.rows | length) > 0 %}
      {% for row in res.rows %}
        {% set tname = row[0] %}
        {% set drop_sql %}
          DROP TABLE IF EXISTS `{{ target.schema }}`.`{{ tname }}`
        {% endset %}
        {% do run_query(drop_sql) %}
      {% endfor %}
    {% endif %}
  {% endif %}
{% endmacro %}
