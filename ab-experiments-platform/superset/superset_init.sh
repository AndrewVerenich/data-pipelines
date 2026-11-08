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
DATABASE_NAME = "ClickHouse A/B"
SQLALCHEMY_URI = "clickhousedb://admin:admin123@clickhouse:8123/ab"


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
        error_body = exc.read().decode()
        print(f"Superset API error {exc.code} for {path}: {error_body}", file=sys.stderr)
        raise


def wait_until_healthy():
    for _ in range(60):
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


def ensure_dataset(token, database_id, table_name):
    datasets = request("/api/v1/dataset/?q=(page:0,page_size:100)", token=token)["result"]
    for dataset in datasets:
        if dataset.get("table_name") == table_name and dataset.get("database", {}).get("id") == database_id:
            request(f"/api/v1/dataset/{dataset['id']}", method="DELETE", token=token)
            break
    created = request(
        "/api/v1/dataset/",
        method="POST",
        payload={"database": database_id, "schema": "ab", "table_name": table_name},
        token=token,
    )
    return created.get("id") or created["result"]["id"]


def ensure_chart(token, title, dataset_id, viz_type, params, query_context):
    charts = request("/api/v1/chart/?q=(page:0,page_size:100)", token=token)["result"]
    existing = find_by(charts, "slice_name", title)
    if existing:
        connection = sqlite3.connect("/app/superset_home/superset.db")
        cursor = connection.cursor()
        cursor.execute(
            """
            UPDATE slices
            SET slice_name = ?, datasource_id = ?, datasource_type = ?, viz_type = ?, params = ?, query_context = ?
            WHERE id = ?
            """,
            (title, dataset_id, "table", viz_type, json.dumps(params), json.dumps(query_context), existing["id"]),
        )
        connection.commit()
        connection.close()
        return existing["id"]
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


def table_chart(token, title, dataset_id, columns):
    params = {
        "datasource": f"{dataset_id}__table",
        "viz_type": "table",
        "all_columns": columns,
        "row_limit": 1000,
        "table_timestamp_format": "smart_date",
    }
    query_context = {
        "datasource": {"id": dataset_id, "type": "table"},
        "queries": [{"columns": columns, "row_limit": 1000, "orderby": []}],
        "form_data": params,
        "result_format": "json",
        "result_type": "full",
    }
    return ensure_chart(token, title, dataset_id, "table", params, query_context)


def timeseries_chart(token, title, dataset_id):
    metric = {
        "expressionType": "SQL",
        "sqlExpression": "AVG(conversion_rate)",
        "label": "conversion_rate",
        "optionName": "conversion_rate",
    }
    params = {
        "datasource": f"{dataset_id}__table",
        "viz_type": "echarts_timeseries_line",
        "x_axis": "day",
        "metrics": [metric],
        "groupby": ["experiment_key", "variant_key"],
        "row_limit": 5000,
        "time_range": "No filter",
    }
    query_context = {
        "datasource": {"id": dataset_id, "type": "table"},
        "queries": [
            {
                "time_range": "No filter",
                "columns": ["experiment_key", "variant_key"],
                "metrics": [metric],
                "row_limit": 5000,
                "is_timeseries": True,
            }
        ],
        "form_data": params,
        "result_format": "json",
        "result_type": "full",
    }
    return ensure_chart(token, title, dataset_id, "echarts_timeseries_line", params, query_context)


def ensure_dashboard(token, title):
    dashboards = request("/api/v1/dashboard/?q=(page:0,page_size:100)", token=token)["result"]
    existing = find_by(dashboards, "dashboard_title", title)
    if existing:
        return existing["id"]
    created = request(
        "/api/v1/dashboard/",
        method="POST",
        payload={"dashboard_title": title, "published": True},
        token=token,
    )
    return created.get("id") or created["result"]["id"]


def wire_dashboard(dashboard_id, chart_ids):
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


wait_until_healthy()
token = login()
database_id = ensure_database(token)
decisions_ds = ensure_dataset(token, database_id, "v_experiment_decisions")
metrics_ds = ensure_dataset(token, database_id, "v_metric_summary")
daily_ds = ensure_dataset(token, database_id, "v_daily_conversion")

chart_ids = [
    table_chart(
        token,
        "Experiment decisions",
        decisions_ds,
        ["experiment_key", "decision", "control_n", "treatment_n", "srm_p_value", "conversion_lift", "conversion_p_value"],
    ),
    table_chart(
        token,
        "Metric summary (lift / CI / CUPED)",
        metrics_ds,
        ["experiment_key", "metric_key", "role", "control_mean", "treatment_mean", "lift", "ci_low", "ci_high", "p_value", "cuped_lift", "cuped_p_value", "decision"],
    ),
    timeseries_chart(token, "Daily conversion by variant", daily_ds),
]
dashboard_id = ensure_dashboard(token, "A/B Experiments")
wire_dashboard(dashboard_id, chart_ids)
print(f"Superset ready: dashboard id={dashboard_id}")
PY
