#!/bin/bash
set -euo pipefail

superset db upgrade
superset fab create-admin \
  --username admin \
  --firstname Superset \
  --lastname Admin \
  --email admin@superset.com \
  --password admin || true
superset init

superset run -p 8088 -h 0.0.0.0 &
SUPERSET_PID=$!

# Dashboard bootstrap can retry; keep the webserver up either way.
set +e
for attempt in 1 2 3 4 5; do
  echo "Superset dashboard bootstrap attempt ${attempt}/5"
  if bash /superset/superset_init.sh; then
    echo "Superset dashboard bootstrap OK"
    break
  fi
  echo "Bootstrap failed; retrying in 15s..."
  sleep 15
done
set -e

wait "$SUPERSET_PID"
