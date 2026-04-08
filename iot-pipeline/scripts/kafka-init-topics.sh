#!/bin/bash
set -e
BOOT="${KAFKA_BOOTSTRAP:-kafka:9092}"
topics=(
  sensor.temperature
  sensor.humidity
  sensor.motion
  sensor.door-window
  sensor.light-level
  command.hvac
  command.lighting
  alert.security
  alert.device-health
  analytics.climate
)
for t in "${topics[@]}"; do
  kafka-topics --create --if-not-exists --bootstrap-server "$BOOT" --topic "$t" --partitions 3 --replication-factor 1
done
echo "Kafka topics ready."
