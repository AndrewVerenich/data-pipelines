#!/bin/bash
sleep 10
curl -X POST http://debezium:8083/connectors -H "Content-Type: application/json" -d '{
  "name": "iot-sensor-connector",
  "config": {
    "connector.class": "io.debezium.connector.postgresql.PostgresConnector",
    "database.hostname": "postgres",
    "database.port": "5432",
    "database.user": "admin",
    "database.password": "admin123",
    "database.dbname": "iot_db",
    "database.server.name": "postgres",
    "table.include.list": "public.sensor_data",
    "plugin.name": "pgoutput",
    "slot.name": "sensor_slot",
    "topic.prefix": "iot",
    "publication.name": "sensor_pub",
    "database.history.kafka.bootstrap.servers": "kafka:9092",
    "database.history.kafka.topic": "schema-changes.sensor"
  }
}'
