import time
from datetime import datetime, timedelta

import requests
from airflow import DAG
from airflow.operators.python import PythonOperator
from airflow.providers.docker.operators.docker import DockerOperator
from airflow.utils.task_group import TaskGroup

LIVY_URL = "http://livy:8998"
DOCKER_NETWORK = "ecommerce-pipeline_hadoop"
DBT_IMAGE = "ecommerce-pipeline-dbt:latest"

default_args = {
    "owner": "airflow",
    "retries": 2,
    "retry_delay": timedelta(minutes=10),
}


def _on_failure(ctx):
    print("Task failed:", ctx.get("task_instance_key_str"))


def set_batch_id(**context):
    rid = context["run_id"].replace(":", "_").replace("+", "_")[:80]
    return f"{context['ds_nodash']}_{rid}"


def check_clickhouse(**_):
    r = requests.get("http://clickhouse:8123/ping", timeout=15)
    r.raise_for_status()


def check_livy(**_):
    # Livy 0.8 has no /server-version; use standard REST list endpoint
    r = requests.get(f"{LIVY_URL}/sessions", timeout=15)
    r.raise_for_status()


def _submit_and_wait(step: str, **context):
    batch_id = context["ti"].xcom_pull(task_ids="set_batch_id")
    body = {
        "file": "local:/opt/spark-app/spark-app-1.0-all.jar",
        "className": "com.example.EcommerceSparkPipeline",
        "conf": {
            "spark.master": "spark://spark-master:7077",
            "spark.executor.memory": "2g",
            "spark.executor.cores": "2",
            "spark.livy.job.timeout": "600s",
        },
        "args": ["--step", step, "--batch-id", batch_id],
    }
    r = requests.post(
        f"{LIVY_URL}/batches",
        json=body,
        headers={"Content-Type": "application/json"},
        timeout=60,
    )
    r.raise_for_status()
    batch_num = r.json()["id"]
    deadline = time.time() + 900
    while time.time() < deadline:
        st = requests.get(f"{LIVY_URL}/batches/{batch_num}/state", timeout=30)
        st.raise_for_status()
        state = st.json().get("state")
        if state in ("success", "dead", "killed", "error"):
            if state != "success":
                raise RuntimeError(f"Livy batch {batch_num} ({step}) state={state}")
            return
        time.sleep(8)
    raise TimeoutError(f"Livy batch {batch_num} ({step}) polling timeout")


with DAG(
    dag_id="ecommerce_dwh_pipeline",
    default_args=default_args,
    schedule_interval="@hourly",
    start_date=datetime(2026, 1, 1),
    catchup=False,
    is_paused_upon_creation=False,
    tags=["ecommerce", "spark", "clickhouse", "dbt"],
    on_failure_callback=_on_failure,
) as dag:
    batch = PythonOperator(
        task_id="set_batch_id",
        python_callable=set_batch_id,
    )

    prepare_ch = PythonOperator(
        task_id="check_clickhouse",
        python_callable=check_clickhouse,
    )
    prepare_livy = PythonOperator(
        task_id="check_livy",
        python_callable=check_livy,
    )

    spark_bronze = PythonOperator(
        task_id="spark_bronze",
        python_callable=_submit_and_wait,
        op_kwargs={"step": "bronze"},
    )
    spark_silver = PythonOperator(
        task_id="spark_silver",
        python_callable=_submit_and_wait,
        op_kwargs={"step": "silver"},
    )
    spark_load_ch = PythonOperator(
        task_id="spark_load_clickhouse",
        python_callable=_submit_and_wait,
        op_kwargs={"step": "load_ch"},
    )

    with TaskGroup(group_id="dbt_transform") as dbt_transform:
        dbt_seed = DockerOperator(
            task_id="dbt_seed",
            image=DBT_IMAGE,
            command="seed --full-refresh",
            docker_url="unix://var/run/docker.sock",
            network_mode=DOCKER_NETWORK,
            mount_tmp_dir=False,
        )
        run_staging = DockerOperator(
            task_id="run_staging",
            image=DBT_IMAGE,
            command="run --select tag:staging",
            docker_url="unix://var/run/docker.sock",
            network_mode=DOCKER_NETWORK,
            mount_tmp_dir=False,
        )
        run_intermediate = DockerOperator(
            task_id="run_intermediate",
            image=DBT_IMAGE,
            command="run --select tag:intermediate",
            docker_url="unix://var/run/docker.sock",
            network_mode=DOCKER_NETWORK,
            mount_tmp_dir=False,
        )
        run_dimensions = DockerOperator(
            task_id="run_dimensions",
            image=DBT_IMAGE,
            command="run --select tag:dimensions",
            docker_url="unix://var/run/docker.sock",
            network_mode=DOCKER_NETWORK,
            mount_tmp_dir=False,
        )
        run_facts = DockerOperator(
            task_id="run_facts",
            image=DBT_IMAGE,
            command="run --select tag:facts",
            docker_url="unix://var/run/docker.sock",
            network_mode=DOCKER_NETWORK,
            mount_tmp_dir=False,
        )
        run_marts = DockerOperator(
            task_id="run_marts",
            image=DBT_IMAGE,
            command="run --select tag:marts",
            docker_url="unix://var/run/docker.sock",
            network_mode=DOCKER_NETWORK,
            mount_tmp_dir=False,
        )
        dbt_seed >> run_staging >> run_intermediate >> run_dimensions >> run_facts >> run_marts

    dbt_test = DockerOperator(
        task_id="dbt_test",
        image=DBT_IMAGE,
        command="test",
        docker_url="unix://var/run/docker.sock",
        network_mode=DOCKER_NETWORK,
        mount_tmp_dir=False,
    )

    batch >> [prepare_ch, prepare_livy] >> spark_bronze >> spark_silver >> spark_load_ch
    spark_load_ch >> dbt_transform >> dbt_test
