from airflow import DAG
from airflow.operators.python import PythonOperator
from airflow.providers.http.operators.http import HttpOperator
from airflow.providers.http.sensors.http import HttpSensor
from airflow.utils.dates import days_ago
import json
import requests

default_args = {
    'owner': 'airflow',
    'retries': 1,
}

def extract_batch_id(response, **context):
    batch_id = response.json()['id']
    context['ti'].xcom_push(key='batch_id', value=batch_id)

def check_livy_status(**context):
    batch_id = context['ti'].xcom_pull(key='batch_id')
    url = f"http://livy:8998/batches/{batch_id}"
    response = requests.get(url)
    state = response.json()['state']
    if state in ['success', 'dead']:
        return True
    return False

with DAG(
    dag_id='spark_via_livy',
    default_args=default_args,
    schedule_interval='@hourly',
    start_date=days_ago(1),
    catchup=False,
    is_paused_upon_creation=False,
) as dag:

    submit_job = HttpOperator(
        task_id='submit_spark_job',
        http_conn_id='livy_default',
        endpoint='batches',
        method='POST',
        headers={"Content-Type": "application/json"},
        data=json.dumps({
            "file": "local:/opt/spark-app/spark-app-1.0-all.jar",
            "className": "com.example.SimpleSparkJob",
            "conf": {
                "spark.master": "spark://spark-master:7077",
                "spark.executor.memory": "2g",
                "spark.executor.cores": "2",
                "spark.livy.job.timeout": "300s",
                "spark.jars": "local:/opt/jars/postgresql-42.6.2.jar",
                "spark.driver.extraClassPath": "/opt/jars/postgresql-42.6.2.jar",
                "spark.executor.extraClassPath": "/opt/jars/postgresql-42.6.2.jar"
            }
        }),
        response_check=lambda response: response.status_code == 201,
        log_response=True,
        do_xcom_push=True,
    )

    extract_id = PythonOperator(
        task_id='extract_batch_id',
        python_callable=extract_batch_id,
        provide_context=True,
        op_args=["{{ task_instance.xcom_pull(task_ids='submit_spark_job') }}"],
    )

    wait_for_completion = HttpSensor(
        task_id='wait_for_spark_job',
        http_conn_id='livy_default',
        endpoint="{{ ti.xcom_pull(task_ids='extract_batch_id', key='batch_id') | string | format('/batches/%s/state') }}",
        request_params={},
        response_check=lambda response: response.json()['state'] in ['success', 'dead'],
        poke_interval=10,
        timeout=600,
    )

    submit_job >> extract_id >> wait_for_completion
