#!/bin/bash
curl -X POST http://debezium:8083/connectors -H "Content-Type: application/json" -d '{
  "name": "postgres-fintech-connector",
  "config": {
    "connector.class": "io.debezium.connector.postgresql.PostgresConnector",
    "database.hostname": "postgres",
    "database.port": "5432",
    "database.user": "demo",
    "database.password": "demo",
    "database.dbname": "fintech",
    "database.server.name": "postgres",
    "table.include.list": "public.transactions,public.users",
    "plugin.name": "pgoutput",
    "slot.name": "fintech_slot",
    "topic.prefix": "fintech",
    "decimal.handling.mode": "double",
    "publication.name": "fintech_pub",
    "database.history.kafka.bootstrap.servers": "kafka:9092",
    "database.history.kafka.topic": "schema-changes.fintech"
  }
}'
