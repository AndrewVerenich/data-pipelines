from datetime import datetime, timedelta

from airflow import DAG
from airflow.operators.python import PythonOperator
from airflow.providers.docker.operators.docker import DockerOperator
import urllib.request

default_args = {
    "owner": "airflow",
    "retries": 1,
    "retry_delay": timedelta(minutes=2),
}


def _check_clickhouse():
    # Basic readiness; analysis needs user_level populated by CDC.
    req = urllib.request.Request(
        "http://clickhouse:8123/?user=admin&password=admin123",
        data=b"SELECT count() FROM ab.user_level",
    )
    with urllib.request.urlopen(req, timeout=30) as resp:
        body = resp.read().decode().strip()
        if int(body) <= 0:
            raise RuntimeError("ab.user_level is empty; waiting for assignments CDC")


with DAG(
    dag_id="ab_experiments_analysis",
    default_args=default_args,
    description="dbt stats for A/B experiments on ClickHouse",
    schedule_interval="*/5 * * * *",
    start_date=datetime(2025, 1, 1),
    catchup=False,
    max_active_runs=1,
) as dag:
    check_clickhouse = PythonOperator(
        task_id="check_clickhouse_ready",
        python_callable=_check_clickhouse,
    )

    dbt_seed = DockerOperator(
        task_id="dbt_seed",
        image="ab-experiments-dbt:latest",
        command="seed --full-refresh",
        docker_url="unix://var/run/docker.sock",
        network_mode="ab-experiments_default",
        working_dir="/usr/app",
        mount_tmp_dir=False,
        auto_remove="success",
    )

    dbt_run = DockerOperator(
        task_id="dbt_run",
        image="ab-experiments-dbt:latest",
        command="run",
        docker_url="unix://var/run/docker.sock",
        network_mode="ab-experiments_default",
        working_dir="/usr/app",
        mount_tmp_dir=False,
        auto_remove="success",
    )

    dbt_test = DockerOperator(
        task_id="dbt_test",
        image="ab-experiments-dbt:latest",
        command="test",
        docker_url="unix://var/run/docker.sock",
        network_mode="ab-experiments_default",
        working_dir="/usr/app",
        mount_tmp_dir=False,
        auto_remove="success",
    )

    check_clickhouse >> dbt_seed >> dbt_run >> dbt_test
