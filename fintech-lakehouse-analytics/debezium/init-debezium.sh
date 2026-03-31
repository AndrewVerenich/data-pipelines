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
    "table.include.list": "public.customers,public.accounts,public.merchants,public.transactions,public.loans,public.loan_payments,public.exchange_rates",
    "plugin.name": "pgoutput",
    "slot.name": "fintech_slot",
    "topic.prefix": "fintech",
    "decimal.handling.mode": "double",
    "publication.name": "fintech_pub",
    "database.history.kafka.bootstrap.servers": "kafka:9092",
    "database.history.kafka.topic": "schema-changes.fintech"
  }
}'
