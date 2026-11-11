#!/bin/bash
set -euo pipefail

CH_URL="${CLICKHOUSE_HTTP:-http://clickhouse:8123/?user=admin&password=admin123}"
MIN_USERS="${DBT_MIN_USER_LEVEL:-1000}"
MAX_WAIT="${DBT_WAIT_SECONDS:-600}"

echo "Waiting for ab.user_level to reach ${MIN_USERS} rows (CDC + backfill)..."
python3 - <<PY
import os, time, urllib.request

url = os.environ.get("CLICKHOUSE_HTTP", "http://clickhouse:8123/?user=admin&password=admin123")
min_users = int(os.environ.get("DBT_MIN_USER_LEVEL", "1000"))
deadline = time.time() + int(os.environ.get("DBT_WAIT_SECONDS", "600"))

while True:
    n = 0
    try:
        req = urllib.request.Request(url, data=b"SELECT count() FROM ab.user_level", method="POST")
        with urllib.request.urlopen(req, timeout=10) as resp:
            n = int(resp.read().decode().strip() or "0")
    except Exception as exc:
        print(f"  clickhouse not ready: {exc}")
    print(f"  user_level={n}")
    if n >= min_users:
        break
    if time.time() >= deadline:
        print(f"Timed out waiting for user_level; running dbt on current data (n={n})")
        break
    time.sleep(5)
PY

dbt seed --full-refresh
dbt run
dbt test

echo "dbt bootstrap complete"
