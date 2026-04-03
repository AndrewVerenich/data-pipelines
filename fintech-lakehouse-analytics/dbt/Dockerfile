FROM ghcr.io/dbt-labs/dbt-core:1.10.15
RUN pip install dbt-clickhouse


WORKDIR /usr/app

COPY dbt_project.yml /usr/app/dbt_project.yml
COPY packages.yml /usr/app/packages.yml
COPY models /usr/app/models
COPY macros /usr/app/macros
COPY seeds /usr/app/seeds
COPY tests /usr/app/tests

# Airflow DockerOperator starts a fresh container per task; deps in one task are invisible
# to the next. Installing packages at build time keeps dbt_utils/dbt_date available everywhere.
RUN dbt deps

RUN mkdir -p /root/.dbt
COPY profiles.yml /root/.dbt/profiles.yml

ENTRYPOINT ["dbt"]
CMD ["run"]