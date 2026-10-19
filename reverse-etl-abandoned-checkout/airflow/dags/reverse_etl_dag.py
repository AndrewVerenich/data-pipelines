from datetime import datetime, timedelta
import urllib.error
import urllib.request

from airflow import DAG
from airflow.operators.python import PythonOperator
from airflow.providers.docker.operators.docker import DockerOperator

default_args = {
    "owner": "airflow",
    "retries": 1,
    "retry_delay": timedelta(minutes=2),
}

DOCKER_NETWORK = "reverse-etl-abandoned-checkout_default"
DBT_IMAGE = "reverse-etl-abandoned-checkout-dbt:latest"
SYNC_URL = "http://sync-worker:8080/api/sync/run"


def _fail_callback(context):
    print(f"Task failed: {context.get('task_instance_key_str')}")


def check_clickhouse():
    with urllib.request.urlopen("http://clickhouse:8123/ping", timeout=10) as resp:
        if resp.status != 200:
            raise RuntimeError("ClickHouse is not healthy")


def trigger_sync():
    req = urllib.request.Request(SYNC_URL, method="POST", data=b"")
    try:
        with urllib.request.urlopen(req, timeout=120) as resp:
            body = resp.read().decode("utf-8", errors="replace")
            print("sync response:", body)
            if resp.status != 200:
                raise RuntimeError(f"sync HTTP {resp.status}")
    except urllib.error.HTTPError as ex:
        raise RuntimeError(f"sync failed: {ex.read().decode('utf-8', errors='replace')}") from ex


with DAG(
    dag_id="reverse_etl_abandoned_checkout",
    default_args=default_args,
    schedule_interval="*/10 * * * *",
    start_date=datetime(2026, 1, 1),
    catchup=False,
    is_paused_upon_creation=False,
    on_failure_callback=_fail_callback,
    tags=["reverse-etl", "commerce"],
) as dag:
    check_ch = PythonOperator(
        task_id="check_clickhouse",
        python_callable=check_clickhouse,
    )

    dbt_run = DockerOperator(
        task_id="dbt_run",
        image=DBT_IMAGE,
        command="run",
        docker_url="unix://var/run/docker.sock",
        network_mode=DOCKER_NETWORK,
        working_dir="/usr/app",
        mount_tmp_dir=False,
        auto_remove="success",
    )

    dbt_test = DockerOperator(
        task_id="dbt_test",
        image=DBT_IMAGE,
        command="test",
        docker_url="unix://var/run/docker.sock",
        network_mode=DOCKER_NETWORK,
        working_dir="/usr/app",
        mount_tmp_dir=False,
        auto_remove="success",
    )

    sync = PythonOperator(
        task_id="sync_deliver",
        python_callable=trigger_sync,
    )

    check_ch >> dbt_run >> dbt_test >> sync
