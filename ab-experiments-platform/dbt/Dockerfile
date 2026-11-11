FROM ghcr.io/dbt-labs/dbt-core:1.10.15
RUN pip install dbt-clickhouse

WORKDIR /usr/app

COPY dbt_project.yml /usr/app/dbt_project.yml
COPY packages.yml /usr/app/packages.yml
COPY models /usr/app/models
COPY macros /usr/app/macros
COPY seeds /usr/app/seeds
COPY bootstrap.sh /usr/app/bootstrap.sh

RUN chmod +x /usr/app/bootstrap.sh && (dbt deps || true)

RUN mkdir -p /root/.dbt
COPY profiles.yml /root/.dbt/profiles.yml

ENTRYPOINT ["dbt"]
CMD ["run"]
