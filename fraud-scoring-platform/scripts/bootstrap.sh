#!/usr/bin/env bash
# Wait for landing data, run dbt layers, train CatBoost (Kotlin), batch-score.
set -euo pipefail

CLICKHOUSE_HOST="${CLICKHOUSE_HOST:-clickhouse}"
CLICKHOUSE_USER="${CLICKHOUSE_USER:-admin}"
CLICKHOUSE_PASSWORD="${CLICKHOUSE_PASSWORD:-admin123}"

ch_query() {
  curl -sS "http://${CLICKHOUSE_HOST}:8123/" \
    --user "${CLICKHOUSE_USER}:${CLICKHOUSE_PASSWORD}" \
    --data-binary "$1"
}

echo "Waiting for ClickHouse..."
for i in $(seq 1 90); do
  if curl -sf "http://${CLICKHOUSE_HOST}:8123/ping" --user "${CLICKHOUSE_USER}:${CLICKHOUSE_PASSWORD}" >/dev/null; then
    break
  fi
  sleep 2
done

echo "Waiting for landing_transactions to have data..."
for i in $(seq 1 120); do
  cnt=$(ch_query "SELECT count() FROM fraud.landing_transactions" | tr -d '[:space:]' || echo 0)
  echo "  rows=${cnt}"
  if [[ "${cnt}" =~ ^[0-9]+$ ]] && [[ "${cnt}" -ge 1000 ]]; then
    break
  fi
  sleep 5
done

echo "Waiting for labels..."
for i in $(seq 1 60); do
  cnt=$(ch_query "SELECT count() FROM fraud.landing_labels" | tr -d '[:space:]' || echo 0)
  echo "  labels=${cnt}"
  if [[ "${cnt}" =~ ^[0-9]+$ ]] && [[ "${cnt}" -ge 100 ]]; then
    break
  fi
  sleep 5
done

echo "Running dbt..."
dbt deps || true
dbt run --select tag:staging tag:intermediate tag:dimensions tag:facts tag:features
dbt run --select tag:marts || true
dbt test || true

echo "Training CatBoost (Kotlin ml-jobs)..."
java -jar /ml/fraud-ml-jobs-1.0.0.jar train

echo "Batch scoring..."
java -jar /ml/fraud-ml-jobs-1.0.0.jar score-batch

echo "Refreshing marts after scores..."
dbt run --select tag:marts || true

echo "Bootstrap complete"
