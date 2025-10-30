from airflow import DAG
from airflow.operators.bash import BashOperator
from datetime import datetime, timedelta

default_args = {
    'owner': 'airflow',
    'retries': 1,
    'retry_delay': timedelta(seconds=30),
}

with DAG(
    dag_id='spark_pipeline_every_minute',
    default_args=default_args,
    schedule_interval='* * * * *',
    start_date=datetime(2025, 10, 30),
    catchup=False,
    is_paused_upon_creation=False,
    tags=['spark', 'hdfs'],
) as dag:

    run_spark = BashOperator(
        task_id='run_spark_job',
        bash_command='spark-submit --master spark://spark-master:7077 /opt/spark-jars/spark-app-1.0-all.jar',
    )
