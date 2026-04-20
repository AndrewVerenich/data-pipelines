from datetime import datetime, timedelta
import json
import urllib.request

from airflow import DAG
from airflow.operators.python import PythonOperator
from airflow.providers.docker.operators.docker import DockerOperator
from airflow.utils.task_group import TaskGroup

SPARK_IMAGE = "banking-lakehouse-spark:latest"
DOCKER_NETWORK = "banking-lakehouse_lakehouse-net"
SPARK_MASTER = "spark://spark-master:7077"

SPARK_CONF = [
    "spark.sql.catalog.iceberg=org.apache.iceberg.spark.SparkCatalog",
    "spark.sql.catalog.iceberg.type=jdbc",
    "spark.sql.catalog.iceberg.uri=jdbc:postgresql://postgres:5432/iceberg_catalog",
    "spark.sql.catalog.iceberg.jdbc.user=admin",
    "spark.sql.catalog.iceberg.jdbc.password=admin123",
    "spark.sql.catalog.iceberg.warehouse=s3a://lakehouse/",
    "spark.sql.catalog.iceberg.io-impl=org.apache.iceberg.aws.s3.S3FileIO",
    "spark.sql.catalog.iceberg.s3.endpoint=http://minio:9000",
    "spark.sql.catalog.iceberg.s3.path-style-access=true",
    "spark.hadoop.fs.s3a.endpoint=http://minio:9000",
    "spark.hadoop.fs.s3a.access.key=admin",
    "spark.hadoop.fs.s3a.secret.key=admin123",
    "spark.hadoop.fs.s3a.path.style.access=true",
    "spark.hadoop.fs.s3a.impl=org.apache.hadoop.fs.s3a.S3AFileSystem",
]

default_args = {
    "owner": "airflow",
    "retries": 1,
    "retry_delay": timedelta(minutes=5),
}


def _check_http(url: str):
    with urllib.request.urlopen(url, timeout=10) as response:
        if response.status != 200:
            raise RuntimeError(f"Health check failed for {url}")


def _run_trino_query(sql: str):
    headers = {
        "X-Trino-User": "airflow",
        "X-Trino-Catalog": "iceberg",
        "X-Trino-Schema": "gold",
    }
    req = urllib.request.Request(
        "http://trino:8080/v1/statement",
        data=sql.encode("utf-8"),
        method="POST",
        headers=headers,
    )
    with urllib.request.urlopen(req, timeout=30) as response:
        payload = json.loads(response.read().decode("utf-8"))

    while True:
        if payload.get("error"):
            raise RuntimeError(f"Trino query failed: {payload['error']}")

        if payload.get("data") is not None:
            return payload["data"]

        next_uri = payload.get("nextUri")
        if not next_uri:
            return []

        next_req = urllib.request.Request(
            next_uri,
            method="GET",
            headers={"X-Trino-User": "airflow"},
        )
        with urllib.request.urlopen(next_req, timeout=30) as response:
            payload = json.loads(response.read().decode("utf-8"))


def _data_quality_check():
    marts = [
        "spending_by_category",
        "customer_segments",
        "anomaly_flags",
        "monthly_cashflow",
        "channel_analysis",
    ]
    for mart in marts:
        rows = _run_trino_query(f"SELECT COUNT(*) FROM iceberg.gold.{mart}")
        if not rows or rows[0][0] == 0:
            raise RuntimeError(f"Data quality check failed: iceberg.gold.{mart} is empty")


def _spark_submit(main_class: str, args: str) -> str:
    conf_part = " ".join([f"--conf {value}" for value in SPARK_CONF])
    return (
        f"/opt/spark/bin/spark-submit --master {SPARK_MASTER} {conf_part} "
        f"--class {main_class} /opt/spark-jobs/banking-spark-jobs.jar {args}"
    )


with DAG(
    dag_id="banking_lakehouse_daily",
    default_args=default_args,
    schedule_interval="@daily",
    start_date=datetime(2026, 1, 1),
    catchup=False,
    tags=["banking", "lakehouse", "spark", "iceberg"],
) as dag:
    check_minio = PythonOperator(
        task_id="check_minio",
        python_callable=lambda: _check_http("http://minio:9000/minio/health/live"),
    )
    check_spark = PythonOperator(
        task_id="check_spark",
        python_callable=lambda: _check_http("http://spark-master:8080"),
    )

    with TaskGroup(group_id="bronze_to_silver") as bronze_to_silver:
        spark_silver_customers = DockerOperator(
            task_id="spark_silver_customers",
            image=SPARK_IMAGE,
            command=_spark_submit(
                "com.example.banking.spark.BronzeToSilverJob",
                "--table customers --wait-seconds 300 --wait-interval-seconds 10",
            ),
            docker_url="unix://var/run/docker.sock",
            network_mode=DOCKER_NETWORK,
            mount_tmp_dir=False,
            auto_remove=True,
        )
        spark_silver_accounts = DockerOperator(
            task_id="spark_silver_accounts",
            image=SPARK_IMAGE,
            command=_spark_submit(
                "com.example.banking.spark.BronzeToSilverJob",
                "--table accounts --wait-seconds 300 --wait-interval-seconds 10",
            ),
            docker_url="unix://var/run/docker.sock",
            network_mode=DOCKER_NETWORK,
            mount_tmp_dir=False,
            auto_remove=True,
        )
        spark_silver_transactions = DockerOperator(
            task_id="spark_silver_transactions",
            image=SPARK_IMAGE,
            command=_spark_submit(
                "com.example.banking.spark.BronzeToSilverJob",
                "--table transactions --wait-seconds 300 --wait-interval-seconds 10",
            ),
            docker_url="unix://var/run/docker.sock",
            network_mode=DOCKER_NETWORK,
            mount_tmp_dir=False,
            auto_remove=True,
        )

    with TaskGroup(group_id="silver_to_gold") as silver_to_gold:
        spark_gold_spending = DockerOperator(
            task_id="spark_gold_spending",
            image=SPARK_IMAGE,
            command=_spark_submit("com.example.banking.spark.SilverToGoldJob", "--mart spending_by_category"),
            docker_url="unix://var/run/docker.sock",
            network_mode=DOCKER_NETWORK,
            mount_tmp_dir=False,
            auto_remove=True,
        )
        spark_gold_segments = DockerOperator(
            task_id="spark_gold_segments",
            image=SPARK_IMAGE,
            command=_spark_submit("com.example.banking.spark.SilverToGoldJob", "--mart customer_segments"),
            docker_url="unix://var/run/docker.sock",
            network_mode=DOCKER_NETWORK,
            mount_tmp_dir=False,
            auto_remove=True,
        )
        spark_gold_anomalies = DockerOperator(
            task_id="spark_gold_anomalies",
            image=SPARK_IMAGE,
            command=_spark_submit("com.example.banking.spark.SilverToGoldJob", "--mart anomaly_flags"),
            docker_url="unix://var/run/docker.sock",
            network_mode=DOCKER_NETWORK,
            mount_tmp_dir=False,
            auto_remove=True,
        )
        spark_gold_cashflow = DockerOperator(
            task_id="spark_gold_cashflow",
            image=SPARK_IMAGE,
            command=_spark_submit("com.example.banking.spark.SilverToGoldJob", "--mart monthly_cashflow"),
            docker_url="unix://var/run/docker.sock",
            network_mode=DOCKER_NETWORK,
            mount_tmp_dir=False,
            auto_remove=True,
        )
        spark_gold_channels = DockerOperator(
            task_id="spark_gold_channels",
            image=SPARK_IMAGE,
            command=_spark_submit("com.example.banking.spark.SilverToGoldJob", "--mart channel_analysis"),
            docker_url="unix://var/run/docker.sock",
            network_mode=DOCKER_NETWORK,
            mount_tmp_dir=False,
            auto_remove=True,
        )

    data_quality_check = PythonOperator(
        task_id="data_quality_check",
        python_callable=_data_quality_check,
    )

    [check_minio, check_spark] >> bronze_to_silver >> silver_to_gold >> data_quality_check
