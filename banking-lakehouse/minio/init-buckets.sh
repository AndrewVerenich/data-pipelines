#!/bin/sh
set -e

mc alias set local http://minio:9000 admin admin123
mc mb --ignore-existing local/lakehouse
mc anonymous set download local/lakehouse

echo "Bucket lakehouse is ready"
