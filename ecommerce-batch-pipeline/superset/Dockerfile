FROM apache/superset:latest

USER root

# Восстанавливаем pip внутри venv и ставим psycopg2-binary
RUN /app/.venv/bin/python -m ensurepip && \
    /app/.venv/bin/python -m pip install --upgrade pip && \
    /app/.venv/bin/python -m pip install psycopg2-binary

USER superset
