from airflow import DAG
from airflow.providers.docker.operators.docker import DockerOperator
from datetime import datetime, timedelta

default_args = {
    'owner': 'airflow',
    'retries': 1,
    'retry_delay': timedelta(minutes=5),
}

with DAG(
        'dbt_run_every_hour',
        default_args=default_args,
        schedule_interval='@hourly',
        start_date=datetime(2025, 11, 23),
        catchup=False,
) as dag:
    run_dbt = DockerOperator(
        task_id='run_dbt',
        image='fintech-lakehouse-analytics-dbt:latest',
        command='run',
        docker_url='unix://var/run/docker.sock',
        network_mode='fintech-lakehouse-analytics_my-network',
        working_dir='/usr/app',
        mount_tmp_dir=False
    )
