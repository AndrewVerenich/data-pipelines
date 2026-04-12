FROM ghcr.io/dbt-labs/dbt-core:1.10.15
RUN pip install --no-cache-dir dbt-clickhouse

WORKDIR /usr/app

COPY dbt_project.yml /usr/app/dbt_project.yml
COPY packages.yml /usr/app/packages.yml
COPY models /usr/app/models
COPY macros /usr/app/macros
COPY seeds /usr/app/seeds
COPY tests /usr/app/tests

RUN dbt deps

RUN mkdir -p /root/.dbt
COPY profiles.yml /root/.dbt/profiles.yml

ENTRYPOINT ["dbt"]
CMD ["run"]
