from datetime import datetime, timedelta

from airflow import DAG
from airflow.operators.python import PythonOperator
from airflow.providers.docker.operators.docker import DockerOperator
from airflow.utils.task_group import TaskGroup
from docker.types import Mount
import urllib.request

default_args = {
    "owner": "airflow",
    "retries": 1,
    "retry_delay": timedelta(minutes=5),
}

NETWORK = "fraud-scoring-platform_my-network"
DBT_IMAGE = "fraud-scoring-platform-dbt:latest"
ML_IMAGE = "fraud-scoring-platform-ml:latest"
MODEL_MOUNT = Mount(
    source="fraud-scoring-platform_model-registry",
    target="/models",
    type="volume",
)


def _check_clickhouse():
    with urllib.request.urlopen("http://clickhouse:8123/ping", timeout=10) as resp:
        if resp.status != 200:
            raise RuntimeError("ClickHouse is not healthy")


with DAG(
    "fraud_scoring_daily",
    default_args=default_args,
    schedule_interval="@hourly",
    start_date=datetime(2026, 1, 1),
    catchup=False,
    tags=["fraud", "catboost", "dbt"],
) as dag:
    check_clickhouse = PythonOperator(
        task_id="check_clickhouse",
        python_callable=_check_clickhouse,
    )

    with TaskGroup(group_id="transform") as transform:
        run_staging = DockerOperator(
            task_id="run_staging",
            image=DBT_IMAGE,
            command="run --select tag:staging",
            docker_url="unix://var/run/docker.sock",
            network_mode=NETWORK,
            working_dir="/usr/app",
            mount_tmp_dir=False,
        )
        run_intermediate = DockerOperator(
            task_id="run_intermediate",
            image=DBT_IMAGE,
            command="run --select tag:intermediate",
            docker_url="unix://var/run/docker.sock",
            network_mode=NETWORK,
            working_dir="/usr/app",
            mount_tmp_dir=False,
        )
        run_dimensions = DockerOperator(
            task_id="run_dimensions",
            image=DBT_IMAGE,
            command="run --select tag:dimensions",
            docker_url="unix://var/run/docker.sock",
            network_mode=NETWORK,
            working_dir="/usr/app",
            mount_tmp_dir=False,
        )
        run_facts = DockerOperator(
            task_id="run_facts",
            image=DBT_IMAGE,
            command="run --select tag:facts",
            docker_url="unix://var/run/docker.sock",
            network_mode=NETWORK,
            working_dir="/usr/app",
            mount_tmp_dir=False,
        )
        run_features = DockerOperator(
            task_id="run_features",
            image=DBT_IMAGE,
            command="run --select tag:features",
            docker_url="unix://var/run/docker.sock",
            network_mode=NETWORK,
            working_dir="/usr/app",
            mount_tmp_dir=False,
        )
        run_marts = DockerOperator(
            task_id="run_marts",
            image=DBT_IMAGE,
            command="run --select tag:marts",
            docker_url="unix://var/run/docker.sock",
            network_mode=NETWORK,
            working_dir="/usr/app",
            mount_tmp_dir=False,
        )
        (
            run_staging
            >> run_intermediate
            >> run_dimensions
            >> run_facts
            >> run_features
            >> run_marts
        )

    dbt_test = DockerOperator(
        task_id="dbt_test",
        image=DBT_IMAGE,
        command="test",
        docker_url="unix://var/run/docker.sock",
        network_mode=NETWORK,
        working_dir="/usr/app",
        mount_tmp_dir=False,
    )

    train_catboost = DockerOperator(
        task_id="train_catboost",
        image=ML_IMAGE,
        command="train",
        docker_url="unix://var/run/docker.sock",
        network_mode=NETWORK,
        mount_tmp_dir=False,
        mounts=[MODEL_MOUNT],
        environment={
            "CLICKHOUSE_JDBC": "jdbc:clickhouse://clickhouse:8123/fraud",
            "CLICKHOUSE_USER": "admin",
            "CLICKHOUSE_PASSWORD": "admin123",
            "MODEL_ROOT": "/models/fraud-models",
            "CATBOOST_BIN": "/usr/local/bin/catboost",
            "SPRING_KAFKA_BOOTSTRAP_SERVERS": "kafka:9092",
        },
    )

    batch_score = DockerOperator(
        task_id="batch_score",
        image=ML_IMAGE,
        command="score-batch",
        docker_url="unix://var/run/docker.sock",
        network_mode=NETWORK,
        mount_tmp_dir=False,
        mounts=[MODEL_MOUNT],
        environment={
            "CLICKHOUSE_JDBC": "jdbc:clickhouse://clickhouse:8123/fraud",
            "CLICKHOUSE_USER": "admin",
            "CLICKHOUSE_PASSWORD": "admin123",
            "MODEL_ROOT": "/models/fraud-models",
            "SPRING_KAFKA_BOOTSTRAP_SERVERS": "kafka:9092",
            "KAFKA_BOOTSTRAP": "kafka:9092",
            "SCORE_BATCH_LIMIT": "5000",
        },
    )

    refresh_marts = DockerOperator(
        task_id="refresh_marts_after_scores",
        image=DBT_IMAGE,
        command="run --select tag:marts",
        docker_url="unix://var/run/docker.sock",
        network_mode=NETWORK,
        working_dir="/usr/app",
        mount_tmp_dir=False,
    )

    check_clickhouse >> transform >> dbt_test >> train_catboost >> batch_score >> refresh_marts
