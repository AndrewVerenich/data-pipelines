#!/usr/bin/env bash
set -euo pipefail

BROKER="${KAFKA_BROKER:-kafka:9092}"

topics=(
  "payments.transactions:6"
  "payments.labels:3"
  "payments.scores:3"
  "payments.users:1"
  "payments.merchants:1"
)

echo "Waiting for Kafka at ${BROKER}..."
for i in $(seq 1 60); do
  if kafka-broker-api-versions --bootstrap-server "${BROKER}" >/dev/null 2>&1; then
    break
  fi
  sleep 2
done

for spec in "${topics[@]}"; do
  name="${spec%%:*}"
  parts="${spec##*:}"
  kafka-topics --bootstrap-server "${BROKER}" \
    --create --if-not-exists \
    --topic "${name}" \
    --partitions "${parts}" \
    --replication-factor 1
  echo "topic ready: ${name}"
done

echo "Kafka topics initialized"
