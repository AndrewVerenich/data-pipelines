FROM apache/superset:3.1.0

USER root

RUN apt-get update && apt-get install -y --no-install-recommends curl && rm -rf /var/lib/apt/lists/*
RUN pip install --no-cache-dir "clickhouse-connect<0.8"

COPY docker-entrypoint.sh /superset/docker-entrypoint.sh
COPY superset_init.sh /superset/superset_init.sh
COPY superset_config.py /app/superset_home/superset_config.py

RUN chmod +x /superset/docker-entrypoint.sh /superset/superset_init.sh && \
    chown superset:superset /superset/docker-entrypoint.sh /superset/superset_init.sh /app/superset_home/superset_config.py

USER superset

CMD ["/superset/docker-entrypoint.sh"]
