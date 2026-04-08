#!/bin/sh
set -e
BOOT="${KAFKA_BOOTSTRAP:-kafka:9092}"
TOPIC="${DEBEZIUM_TOPIC:-iot.public.room_config}"
i=0
while [ "$i" -lt 90 ]; do
  if kafka-topics --bootstrap-server "$BOOT" --list 2>/dev/null | grep -q "^${TOPIC}$"; then
    echo "Topic $TOPIC is ready."
    exit 0
  fi
  i=$((i + 1))
  echo "Waiting for $TOPIC... ($i)"
  sleep 2
done
echo "Timeout waiting for $TOPIC"
exit 1
