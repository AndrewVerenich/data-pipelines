#!/bin/bash
set -euo pipefail

echo "Waiting for HDFS..."
sleep 25

echo "Default FS:"
hdfs getconf -confKey fs.defaultFS

echo "Loading raw layout into HDFS..."
hdfs dfs -mkdir -p /raw/events /raw/reference /processed/bronze /processed/silver
hdfs dfs -put -f /data/events.jsonl /raw/events/events.jsonl
hdfs dfs -put -f /data/users.jsonl /raw/reference/users.jsonl
hdfs dfs -put -f /data/products.jsonl /raw/reference/products.jsonl

hdfs dfs -ls -R /raw
echo "HDFS load complete."
