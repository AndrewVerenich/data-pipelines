#!/bin/bash
set -e

superset db upgrade
superset fab create-admin \
   --username admin \
   --firstname Superset \
   --lastname Admin \
   --email admin@superset.com \
   --password admin || true
superset init

python3 - <<'PY'
import json
import sqlite3
import sys
import time
import urllib.error
import urllib.request

BASE_URL = "http://localhost:8088"
USERNAME = "admin"
PASSWORD = "admin"
DATABASE_NAME = "ClickHouse Fraud"
SQLALCHEMY_URI = "clickhousedb://admin:admin123@clickhouse:8123/fraud"


def request(path, method="GET", payload=None, token=None):
    headers = {}
    data = None
    if token:
        headers["Authorization"] = f"Bearer {token}"
    if payload is not None:
        headers["Content-Type"] = "application/json"
        data = json.dumps(payload).encode()
    req = urllib.request.Request(f"{BASE_URL}{path}", data=data, headers=headers, method=method)
    try:
        with urllib.request.urlopen(req) as response:
            body = response.read().decode()
            return json.loads(body) if body else {}
    except urllib.error.HTTPError as exc:
        print(f"Superset API error {exc.code} for {path}: {exc.read().decode()}", file=sys.stderr)
        raise


def wait_until_healthy():
    for _ in range(90):
        try:
            with urllib.request.urlopen(f"{BASE_URL}/health") as response:
                if response.status == 200:
                    return
        except Exception:
            pass
        time.sleep(2)
    raise RuntimeError("Superset did not become healthy in time")


def login():
    response = request(
        "/api/v1/security/login",
        method="POST",
        payload={"username": USERNAME, "password": PASSWORD, "provider": "db", "refresh": True},
    )
    return response["access_token"]


def find_by(items, key, value):
    for item in items:
        if item.get(key) == value:
            return item
    return None


def ensure_database(token):
    databases = request("/api/v1/database/?q=(page:0,page_size:100)", token=token)["result"]
    existing = find_by(databases, "database_name", DATABASE_NAME)
    if existing:
        return existing["id"]
    created = request(
        "/api/v1/database/",
        method="POST",
        payload={"database_name": DATABASE_NAME, "sqlalchemy_uri": SQLALCHEMY_URI},
        token=token,
    )
    return created.get("id") or created["result"]["id"]


def ensure_dataset(token, database_id, schema, table_name):
    datasets = request("/api/v1/dataset/?q=(page:0,page_size:200)", token=token)["result"]
    for dataset in datasets:
        if (
            dataset.get("table_name") == table_name
            and dataset.get("schema") == schema
            and dataset.get("database", {}).get("id") == database_id
        ):
            return dataset["id"]
    created = request(
        "/api/v1/dataset/",
        method="POST",
        payload={"database": database_id, "schema": schema, "table_name": table_name},
        token=token,
    )
    return created.get("id") or created["result"]["id"]


def ensure_chart(token, title, dataset_id, viz_type, params):
    charts = request("/api/v1/chart/?q=(page:0,page_size:200)", token=token)["result"]
    existing = find_by(charts, "slice_name", title)
    if existing:
        return existing["id"]
    query_context = {
        "datasource": {"id": dataset_id, "type": "table"},
        "queries": [{"metrics": params.get("metrics", []), "columns": params.get("groupby", []), "row_limit": 1000}],
        "form_data": params,
        "result_format": "json",
        "result_type": "full",
    }
    created = request(
        "/api/v1/chart/",
        method="POST",
        payload={
            "slice_name": title,
            "viz_type": viz_type,
            "datasource_id": dataset_id,
            "datasource_type": "table",
            "params": json.dumps(params),
            "query_context": json.dumps(query_context),
        },
        token=token,
    )
    return created.get("id") or created["result"]["id"]


def ensure_dashboard(token, title, chart_ids):
    dashboards = request("/api/v1/dashboard/?q=(page:0,page_size:100)", token=token)["result"]
    existing = find_by(dashboards, "dashboard_title", title)
    if existing:
        dashboard_id = existing["id"]
    else:
        created = request(
            "/api/v1/dashboard/",
            method="POST",
            payload={"dashboard_title": title, "published": True},
            token=token,
        )
        dashboard_id = created.get("id") or created["result"]["id"]

    connection = sqlite3.connect("/app/superset_home/superset.db")
    cursor = connection.cursor()
    cursor.execute("DELETE FROM dashboard_slices WHERE dashboard_id = ?", (dashboard_id,))
    for chart_id in chart_ids:
        cursor.execute(
            "INSERT INTO dashboard_slices (dashboard_id, slice_id) VALUES (?, ?)",
            (dashboard_id, chart_id),
        )
    connection.commit()
    connection.close()
    return dashboard_id


def metric(expr, label):
    return {
        "expressionType": "SQL",
        "sqlExpression": expr,
        "label": label,
        "optionName": label,
    }


wait_until_healthy()
token = login()
db_id = ensure_database(token)
ops_ds = ensure_dataset(token, db_id, "fraud", "mart_fraud_ops")
perf_ds = ensure_dataset(token, db_id, "fraud", "mart_model_performance")
risk_ds = ensure_dataset(token, db_id, "fraud", "mart_high_risk_segments")
lag_ds = ensure_dataset(token, db_id, "fraud", "mart_label_lag")

charts = []
charts.append(
    ensure_chart(
        token,
        "Fraud rate by day",
        ops_ds,
        "echarts_timeseries_line",
        {
            "datasource": f"{ops_ds}__table",
            "viz_type": "echarts_timeseries_line",
            "x_axis": "event_date",
            "metrics": [metric("SUM(fraud_cnt) / nullIf(SUM(tx_cnt), 0)", "fraud_rate")],
            "groupby": [],
            "row_limit": 1000,
        },
    )
)
charts.append(
    ensure_chart(
        token,
        "Blocked vs challenged volume",
        ops_ds,
        "echarts_timeseries_line",
        {
            "datasource": f"{ops_ds}__table",
            "viz_type": "echarts_timeseries_line",
            "x_axis": "event_date",
            "metrics": [
                metric("SUM(block_cnt)", "blocks"),
                metric("SUM(challenge_cnt)", "challenges"),
            ],
            "groupby": [],
            "row_limit": 1000,
        },
    )
)
charts.append(
    ensure_chart(
        token,
        "Precision @ 0.7 by day",
        perf_ds,
        "echarts_timeseries_line",
        {
            "datasource": f"{perf_ds}__table",
            "viz_type": "echarts_timeseries_line",
            "x_axis": "event_date",
            "metrics": [metric("AVG(precision_at_0_7)", "precision_0_7")],
            "groupby": [],
            "row_limit": 1000,
        },
    )
)
charts.append(
    ensure_chart(
        token,
        "High-risk segments",
        risk_ds,
        "table",
        {
            "datasource": f"{risk_ds}__table",
            "viz_type": "table",
            "all_columns": ["segment_type", "segment_id", "tx_cnt", "avg_score", "fraud_cnt", "amount_sum"],
            "row_limit": 50,
        },
    )
)
charts.append(
    ensure_chart(
        token,
        "Label coverage",
        lag_ds,
        "echarts_timeseries_line",
        {
            "datasource": f"{lag_ds}__table",
            "viz_type": "echarts_timeseries_line",
            "x_axis": "event_date",
            "metrics": [metric("AVG(label_coverage)", "label_coverage")],
            "groupby": [],
            "row_limit": 1000,
        },
    )
)

dashboard_id = ensure_dashboard(token, "Fraud Scoring Ops", charts)
print(f"Bootstrap done. database={db_id} dashboard={dashboard_id} charts={charts}")
PY
