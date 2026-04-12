#!/bin/bash
set -e

superset run -p 8088 -h 0.0.0.0 &
SUPERSET_PID=$!

sleep 35

sh /superset/superset_init.sh

wait "$SUPERSET_PID"
