#!/usr/bin/env bash
set -euo pipefail

echo "Waiting for MinIO..."
for i in $(seq 1 60); do
  if curl -sf http://minio:9000/minio/health/ready >/dev/null; then
    break
  fi
  sleep 2
done

mc alias set local http://minio:9000 minio minio123
mc mb --ignore-existing local/fraud-models
echo "MinIO bucket fraud-models ready"
