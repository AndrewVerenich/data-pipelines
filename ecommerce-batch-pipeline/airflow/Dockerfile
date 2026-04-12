FROM apache/airflow:2.8.1

USER root
RUN apt-get update \
    && apt-get install -y --no-install-recommends docker.io curl \
    && rm -rf /var/lib/apt/lists/*

USER airflow
