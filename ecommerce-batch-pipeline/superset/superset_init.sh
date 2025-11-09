#!/bin/bash
set -e

# Инициализация базы Superset
superset db upgrade
superset fab create-admin \
   --username admin \
   --firstname Superset \
   --lastname Admin \
   --email admin@superset.com \
   --password admin || true
superset init

#sleep 20

# Логин и получение токена
LOGIN_RESPONSE=$(curl -s \
  -X POST http://localhost:8088/api/v1/security/login \
  -H "Content-Type: application/json" \
  -d '{"username":"admin","password":"admin","provider":"db"}')

ACCESS_TOKEN=$(echo "$LOGIN_RESPONSE" | grep -o '"access_token":"[^"]*"' | sed 's/"access_token":"//;s/"//')
echo "ACCESS_TOKEN: $ACCESS_TOKEN"

# Подключение к Postgres
DB_RESPONSE=$(curl -s -X POST http://localhost:8088/api/v1/database/ \
  -H "Authorization: Bearer $ACCESS_TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "database_name": "Postgres Ecommerce",
    "sqlalchemy_uri": "postgresql+psycopg2://spark:sparkpass@ecommerce-postgres:5432/ecommerce_logs"
  }')

DB_ID=$(echo "$DB_RESPONSE" | grep -o '"id":[0-9]*' | head -n1 | sed 's/"id"://')
echo "DB_ID: $DB_ID"

# Создание dataset
DATASET_RESPONSE=$(curl -s -X POST http://localhost:8088/api/v1/dataset/ \
  -H "Authorization: Bearer $ACCESS_TOKEN" \
  -H "Content-Type: application/json" \
  -d "{
    \"database\": $DB_ID,
    \"schema\": \"public\",
    \"table_name\": \"log_events\"
  }")

DATASET_ID=$(echo "$DATASET_RESPONSE" | grep -o '"id":[0-9]*' | head -n1 | sed 's/\"id\"://')
echo "DATASET_ID: $DATASET_ID"

# Pie Chart
CHART1_RESPONSE=$(curl -s -X POST http://localhost:8088/api/v1/chart/ \
  -H "Authorization: Bearer $ACCESS_TOKEN" \
  -H "Content-Type: application/json" \
  -d "{
    \"slice_name\": \"Events by Type\",
    \"viz_type\": \"pie\",
    \"datasource_id\": $DATASET_ID,
    \"datasource_type\": \"table\",
    \"cache_timeout\": null,
    \"description\": \"Events grouped by type\",
    \"params\": \"{\\\"metrics\\\": [\\\"count\\\"], \\\"groupby\\\": [\\\"event\\\"], \\\"adhoc_filters\\\": [], \\\"row_limit\\\": 100, \\\"time_range\\\": \\\"No filter\\\"}\"
  }")

CHART1_ID=$(echo "$CHART1_RESPONSE" | grep -o '"id":[0-9]*' | head -n1 | sed 's/\"id\"://')
echo "CHART1_ID: $CHART1_ID"

# Bar Chart
CHART2_RESPONSE=$(curl -s -X POST http://localhost:8088/api/v1/chart/ \
  -H "Authorization: Bearer $ACCESS_TOKEN" \
  -H "Content-Type: application/json" \
  -d "{
    \"slice_name\": \"Events per Day\",
    \"viz_type\": \"bar\",
    \"datasource_id\": $DATASET_ID,
    \"datasource_type\": \"table\",
    \"cache_timeout\": null,
    \"description\": \"Events grouped by day\",
    \"params\": \"{\\\"metrics\\\": [\\\"count\\\"], \\\"groupby\\\": [\\\"minute\\\"], \\\"adhoc_filters\\\": [], \\\"row_limit\\\": 100, \\\"time_range\\\": \\\"No filter\\\"}\"
  }")

CHART2_ID=$(echo "$CHART2_RESPONSE" | grep -o '"id":[0-9]*' | head -n1 | sed 's/\"id\"://')
echo "CHART2_ID: $CHART2_ID"

CHART3_RESPONSE=$(curl -s -X POST http://localhost:8088/api/v1/chart/ \
  -H "Authorization: Bearer $ACCESS_TOKEN" \
  -H "Content-Type: application/json" \
  -d "{
    \"slice_name\": \"Errors Over Time\",
    \"viz_type\": \"line\",
    \"datasource_id\": $DATASET_ID,
    \"datasource_type\": \"table\",
    \"description\": \"Error events trend by minute\",
    \"params\": \"{
      \\\"metrics\\\": [\\\"count\\\"],
      \\\"groupby\\\": [\\\"minute\\\"],
      \\\"adhoc_filters\\\": [
        {
          \\\"expressionType\\\": \\\"SIMPLE\\\",
          \\\"subject\\\": \\\"level\\\",
          \\\"operator\\\": \\\"==\\\",
          \\\"comparator\\\": \\\"ERROR\\\",
          \\\"clause\\\": \\\"WHERE\\\",
          \\\"isExtra\\\": false,
          \\\"isNew\\\": false,
          \\\"filterOptionName\\\": \\\"filter_level_ERROR\\\"
        }
      ],
      \\\"row_limit\\\": 100,
      \\\"time_range\\\": \\\"No filter\\\"
    }\"
  }")
CHART3_ID=$(echo "$CHART3_RESPONSE" | grep -o '"id":[0-9]*' | head -n1 | sed 's/\"id\"://')
echo "CHART3_ID: $CHART3_ID"

CHART4_RESPONSE=$(curl -s -X POST http://localhost:8088/api/v1/chart/ \
  -H "Authorization: Bearer $ACCESS_TOKEN" \
  -H "Content-Type: application/json" \
  -d "{
    \"slice_name\": \"Errors by Type\",
    \"viz_type\": \"bar\",
    \"datasource_id\": $DATASET_ID,
    \"datasource_type\": \"table\",
    \"description\": \"Distribution of error types\",
    \"params\": \"{\\\"metrics\\\": [\\\"count\\\"], \\\"groupby\\\": [\\\"errortype\\\"], \\\"adhoc_filters\\\": [], \\\"row_limit\\\": 100, \\\"time_range\\\": \\\"No filter\\\"}\"
  }")
CHART4_ID=$(echo "$CHART4_RESPONSE" | grep -o '"id":[0-9]*' | head -n1 | sed 's/\"id\"://')
echo "CHART4_ID: $CHART4_ID"

CHART5_RESPONSE=$(curl -s -X POST http://localhost:8088/api/v1/chart/ \
  -H "Authorization: Bearer $ACCESS_TOKEN" \
  -H "Content-Type: application/json" \
  -d "{
    \"slice_name\": \"Products by Category\",
    \"viz_type\": \"treemap\",
    \"datasource_id\": $DATASET_ID,
    \"datasource_type\": \"table\",
    \"description\": \"Product views grouped by category\",
    \"params\": \"{\\\"metrics\\\": [\\\"count\\\"], \\\"groupby\\\": [\\\"category\\\"], \\\"adhoc_filters\\\": [], \\\"row_limit\\\": 100, \\\"time_range\\\": \\\"No filter\\\"}\"
  }")
CHART5_ID=$(echo "$CHART5_RESPONSE" | grep -o '"id":[0-9]*' | head -n1 | sed 's/\"id\"://')
echo "CHART5_ID: $CHART5_ID"

CHART6_RESPONSE=$(curl -s -X POST http://localhost:8088/api/v1/chart/ \
  -H "Authorization: Bearer $ACCESS_TOKEN" \
  -H "Content-Type: application/json" \
  -d "{
    \"slice_name\": \"Payments by Method\",
    \"viz_type\": \"bar\",
    \"datasource_id\": $DATASET_ID,
    \"datasource_type\": \"table\",
    \"description\": \"Distribution of payment methods\",
    \"params\": \"{\\\"metrics\\\": [\\\"count\\\"], \\\"groupby\\\": [\\\"paymentmethod\\\"], \\\"adhoc_filters\\\": [], \\\"row_limit\\\": 100, \\\"time_range\\\": \\\"No filter\\\"}\"
  }")
CHART6_ID=$(echo "$CHART6_RESPONSE" | grep -o '"id":[0-9]*' | head -n1 | sed 's/\"id\"://')
echo "CHART6_ID: $CHART6_ID"

# Dashboard
DASHBOARD_RESPONSE=$(curl -s -X POST http://localhost:8088/api/v1/dashboard/ \
  -H "Authorization: Bearer $ACCESS_TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "dashboard_title": "Ecommerce Logs Dashboard",
    "published": true
  }')

DASHBOARD_ID=$(echo "$DASHBOARD_RESPONSE" | grep -o '"id":[0-9]*' | head -n1 | sed 's/\"id\"://')
echo "DASHBOARD_ID: $DASHBOARD_ID"