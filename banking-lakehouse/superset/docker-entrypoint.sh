#!/bin/bash
set -e

superset run -p 8088 -h 0.0.0.0 &
SUPERSET_PID=$!

sleep 35

MAX_INIT_ATTEMPTS=30
INIT_RETRY_DELAY_SECONDS=30
INIT_ATTEMPT=1

while [ "$INIT_ATTEMPT" -le "$MAX_INIT_ATTEMPTS" ]; do
  echo "Superset bootstrap attempt ${INIT_ATTEMPT}/${MAX_INIT_ATTEMPTS}"
  INIT_CODE=0
  sh /superset/superset_init.sh || INIT_CODE=$?

  if [ "$INIT_CODE" -eq 0 ]; then
    echo "Superset bootstrap completed successfully"
    break
  fi

  if [ "$INIT_ATTEMPT" -eq "$MAX_INIT_ATTEMPTS" ]; then
    echo "Superset bootstrap failed after ${MAX_INIT_ATTEMPTS} attempts (last code: $INIT_CODE); webserver stays running for manual checks"
    break
  fi

  echo "Superset bootstrap attempt ${INIT_ATTEMPT} failed with code ${INIT_CODE}; retrying in ${INIT_RETRY_DELAY_SECONDS}s..."
  sleep "$INIT_RETRY_DELAY_SECONDS"
  INIT_ATTEMPT=$((INIT_ATTEMPT + 1))
done

wait "$SUPERSET_PID"
