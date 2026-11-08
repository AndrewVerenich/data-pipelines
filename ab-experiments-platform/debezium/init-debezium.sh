#!/bin/sh
set -e
echo "Waiting for Debezium Connect..."
i=0
while [ "$i" -lt 60 ]; do
  if curl -sf http://debezium:8083/connectors >/dev/null; then
    break
  fi
  i=$((i + 1))
  sleep 2
done

curl -sf -X POST http://debezium:8083/connectors \
  -H "Content-Type: application/json" \
  -d '{
  "name": "ab-postgres-connector",
  "config": {
    "connector.class": "io.debezium.connector.postgresql.PostgresConnector",
    "database.hostname": "postgres",
    "database.port": "5432",
    "database.user": "admin",
    "database.password": "admin123",
    "database.dbname": "ab",
    "table.include.list": "public.assignments,public.outcomes",
    "plugin.name": "pgoutput",
    "slot.name": "ab_slot",
    "topic.prefix": "ab",
    "publication.name": "ab_pub",
    "publication.autocreate.mode": "filtered",
    "snapshot.mode": "initial",
    "decimal.handling.mode": "double",
    "time.precision.mode": "adaptive_time_microseconds",
    "tombstones.on.delete": "false"
  }
}' || echo "Connector may already exist"

echo "Debezium connector ready"
