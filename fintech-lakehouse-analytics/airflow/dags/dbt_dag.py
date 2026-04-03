from airflow import DAG
from airflow.operators.python import PythonOperator
from airflow.utils.task_group import TaskGroup
from airflow.providers.docker.operators.docker import DockerOperator
from datetime import datetime, timedelta
import urllib.request

default_args = {
    'owner': 'airflow',
    'retries': 1,
    'retry_delay': timedelta(minutes=5),
}

def _fail_callback(context):
    print(f"Task failed: {context.get('task_instance_key_str')}")


def _check_clickhouse():
    with urllib.request.urlopen("http://clickhouse:8123/ping", timeout=10) as resp:
        if resp.status != 200:
            raise RuntimeError("ClickHouse is not healthy")


with DAG(
        'fintech_dbt_layered_hourly',
        default_args=default_args,
        schedule_interval='@hourly',
        start_date=datetime(2025, 11, 23),
        catchup=False,
        on_failure_callback=_fail_callback
) as dag:
    check_clickhouse = PythonOperator(
        task_id='check_clickhouse',
        python_callable=_check_clickhouse
    )

    check_data_freshness = PythonOperator(
        task_id='check_data_freshness',
        python_callable=lambda: print("Raw CDC tables assumed fresh for demo")
    )

    # dbt packages are installed in the dbt image (Dockerfile: RUN dbt deps). A separate
    # deps task would not help: each DockerOperator run uses a new container with no shared FS.
    with TaskGroup(group_id="prepare") as prepare:
        dbt_seed = DockerOperator(
            task_id='dbt_seed',
            image='fintech-lakehouse-analytics-dbt:latest',
            command='seed --full-refresh',
            docker_url='unix://var/run/docker.sock',
            network_mode='fintech-lakehouse-analytics_my-network',
            working_dir='/usr/app',
            mount_tmp_dir=False
        )

    with TaskGroup(group_id="transform") as transform:
        run_staging = DockerOperator(
            task_id='run_staging',
            image='fintech-lakehouse-analytics-dbt:latest',
            command='run --select tag:staging',
            docker_url='unix://var/run/docker.sock',
            network_mode='fintech-lakehouse-analytics_my-network',
            working_dir='/usr/app',
            mount_tmp_dir=False
        )
        run_intermediate = DockerOperator(
            task_id='run_intermediate',
            image='fintech-lakehouse-analytics-dbt:latest',
            command='run --select tag:intermediate',
            docker_url='unix://var/run/docker.sock',
            network_mode='fintech-lakehouse-analytics_my-network',
            working_dir='/usr/app',
            mount_tmp_dir=False
        )
        run_dimensions = DockerOperator(
            task_id='run_dimensions',
            image='fintech-lakehouse-analytics-dbt:latest',
            command='run --select tag:dimensions',
            docker_url='unix://var/run/docker.sock',
            network_mode='fintech-lakehouse-analytics_my-network',
            working_dir='/usr/app',
            mount_tmp_dir=False
        )
        run_facts = DockerOperator(
            task_id='run_facts',
            image='fintech-lakehouse-analytics-dbt:latest',
            command='run --select tag:facts',
            docker_url='unix://var/run/docker.sock',
            network_mode='fintech-lakehouse-analytics_my-network',
            working_dir='/usr/app',
            mount_tmp_dir=False
        )
        run_marts = DockerOperator(
            task_id='run_marts',
            image='fintech-lakehouse-analytics-dbt:latest',
            command='run --select tag:marts',
            docker_url='unix://var/run/docker.sock',
            network_mode='fintech-lakehouse-analytics_my-network',
            working_dir='/usr/app',
            mount_tmp_dir=False
        )
        run_staging >> run_intermediate >> run_dimensions >> run_facts >> run_marts

    dbt_test = DockerOperator(
        task_id='dbt_test',
        image='fintech-lakehouse-analytics-dbt:latest',
        command='test',
        docker_url='unix://var/run/docker.sock',
        network_mode='fintech-lakehouse-analytics_my-network',
        working_dir='/usr/app',
        mount_tmp_dir=False
    )

    check_clickhouse >> check_data_freshness >> prepare >> transform >> dbt_test
