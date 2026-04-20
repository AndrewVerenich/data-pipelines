#!/bin/sh
set -e

echo "Waiting Kafka Connect API..."
until curl -sSf "http://kafka-connect:8083/connectors" >/dev/null; do
  sleep 2
done

if curl -fsS "http://kafka-connect:8083/connectors/s3-sink-banking" >/dev/null 2>&1; then
  echo "Connector s3-sink-banking already exists, skipping"
  exit 0
fi

curl -sS -X POST "http://kafka-connect:8083/connectors" \
  -H "Content-Type: application/json" \
  -d '{
    "name": "s3-sink-banking",
    "config": {
      "connector.class": "io.confluent.connect.s3.S3SinkConnector",
      "tasks.max": "1",
      "topics": "banking.customers,banking.accounts,banking.transactions",
      "s3.bucket.name": "lakehouse",
      "s3.region": "us-east-1",
      "store.url": "http://minio:9000",
      "storage.class": "io.confluent.connect.s3.storage.S3Storage",
      "format.class": "io.confluent.connect.s3.format.json.JsonFormat",
      "partitioner.class": "io.confluent.connect.storage.partitioner.DefaultPartitioner",
      "topics.dir": "bronze",
      "flush.size": "100",
      "rotate.interval.ms": "60000",
      "aws.access.key.id": "admin",
      "aws.secret.access.key": "admin123",
      "key.converter": "org.apache.kafka.connect.storage.StringConverter",
      "value.converter": "org.apache.kafka.connect.json.JsonConverter",
      "value.converter.schemas.enable": "false"
    }
  }'

echo
echo "S3 Sink connector registered"
