#!/bin/bash
set -e

superset run -p 8088 -h 0.0.0.0 &
SUPERSET_PID=$!

sleep 35

INIT_CODE=0
sh /superset/superset_init.sh || INIT_CODE=$?
if [ "$INIT_CODE" -ne 0 ]; then
  echo "Superset bootstrap failed with code $INIT_CODE; webserver stays running for manual checks"
fi

wait "$SUPERSET_PID"
