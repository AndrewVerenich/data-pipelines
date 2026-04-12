#!/bin/bash
set -e

superset db upgrade
superset fab create-admin \
  --username admin \
  --firstname Superset \
  --lastname Admin \
  --email admin@superset.com \
  --password admin || true
superset init

sleep 15

LOGIN_RESPONSE=$(curl -s \
  -X POST http://localhost:8088/api/v1/security/login \
  -H "Content-Type: application/json" \
  -d '{"username":"admin","password":"admin","provider":"db"}')

ACCESS_TOKEN=$(echo "$LOGIN_RESPONSE" | grep -o '"access_token":"[^"]*"' | sed 's/"access_token":"//;s/"//')
echo "ACCESS_TOKEN obtained"

DB_RESPONSE=$(curl -s -X POST http://localhost:8088/api/v1/database/ \
  -H "Authorization: Bearer $ACCESS_TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "database_name": "ClickHouse Ecommerce DWH",
    "sqlalchemy_uri": "clickhousedb://admin:admin123@clickhouse:8123/ecommerce_dwh",
    "expose_in_sqllab": true
  }')

DB_ID=$(echo "$DB_RESPONSE" | grep -o '"id":[0-9]*' | head -n1 | sed 's/"id"://')
echo "DB_ID: $DB_ID"

DATASET_RESPONSE=$(curl -s -X POST http://localhost:8088/api/v1/dataset/ \
  -H "Authorization: Bearer $ACCESS_TOKEN" \
  -H "Content-Type: application/json" \
  -d "{
    \"database\": $DB_ID,
    \"schema\": \"ecommerce_dwh\",
    \"table_name\": \"raw_ecommerce_events\"
  }")

DATASET_ID=$(echo "$DATASET_RESPONSE" | grep -o '"id":[0-9]*' | head -n1 | sed 's/\"id\"://')
echo "DATASET_ID: $DATASET_ID"

curl -s -X POST http://localhost:8088/api/v1/chart/ \
  -H "Authorization: Bearer $ACCESS_TOKEN" \
  -H "Content-Type: application/json" \
  -d "{
    \"slice_name\": \"Events by type (raw)\",
    \"viz_type\": \"pie\",
    \"datasource_id\": $DATASET_ID,
    \"datasource_type\": \"table\",
    \"params\": \"{\\\"metrics\\\": [\\\"count\\\"], \\\"groupby\\\": [\\\"event\\\"], \\\"adhoc_filters\\\": [], \\\"row_limit\\\": 100}\"
  }" || true

curl -s -X POST http://localhost:8088/api/v1/chart/ \
  -H "Authorization: Bearer $ACCESS_TOKEN" \
  -H "Content-Type: application/json" \
  -d "{
    \"slice_name\": \"Traffic by device (raw)\",
    \"viz_type\": \"dist_bar\",
    \"datasource_id\": $DATASET_ID,
    \"datasource_type\": \"table\",
    \"params\": \"{\\\"metrics\\\": [\\\"count\\\"], \\\"columns\\\": [\\\"device\\\"], \\\"adhoc_filters\\\": [], \\\"row_limit\\\": 100}\"
  }" || true

curl -s -X POST http://localhost:8088/api/v1/dataset/ \
  -H "Authorization: Bearer $ACCESS_TOKEN" \
  -H "Content-Type: application/json" \
  -d "{
    \"database\": $DB_ID,
    \"schema\": \"ecommerce_dwh\",
    \"table_name\": \"mart_events_by_device\"
  }" || true

echo "Superset init done (ClickHouse). Add charts for marts after first DAG run if datasets were empty."
