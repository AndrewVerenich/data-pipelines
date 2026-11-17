-- experiment_results and Superset-facing views are built by dbt (Airflow every 5 min).
-- Keep database marker so init order stays explicit.
SELECT 'ab analytics layer owned by dbt' AS note;
