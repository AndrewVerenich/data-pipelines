{% macro drop_clickhouse_dbt_backups() %}
  {% if execute %}
    {% set sql %}
      SELECT name
      FROM system.tables
      WHERE database = currentDatabase()
        AND name LIKE '%__dbt_backup'
    {% endset %}
    {% set results = run_query(sql) %}
    {% if results is not none %}
      {% for row in results.rows %}
        {% do run_query('DROP TABLE IF EXISTS ' ~ row[0]) %}
      {% endfor %}
    {% endif %}
  {% endif %}
{% endmacro %}
