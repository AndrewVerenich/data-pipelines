#!/bin/bash
sleep 15
curl -sf -X POST http://debezium:8083/connectors -H "Content-Type: application/json" -d '{
  "name": "iot-room-config-connector",
  "config": {
    "connector.class": "io.debezium.connector.postgresql.PostgresConnector",
    "database.hostname": "postgres",
    "database.port": "5432",
    "database.user": "admin",
    "database.password": "admin123",
    "database.dbname": "iot_db",
    "table.include.list": "public.room_config",
    "plugin.name": "pgoutput",
    "slot.name": "room_config_slot",
    "topic.prefix": "iot",
    "publication.name": "room_config_pub",
    "publication.autocreate.mode": "filtered",
    "database.history.kafka.bootstrap.servers": "kafka:9092",
    "database.history.kafka.topic": "schema-changes.iot-room",
    "snapshot.mode": "initial"
  }
}' || echo "Connector may already exist."
