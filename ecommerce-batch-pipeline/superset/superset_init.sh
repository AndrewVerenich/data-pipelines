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
DATABASE_NAME = "ClickHouse Ecommerce DWH"
SQLALCHEMY_URI = "clickhousedb://admin:admin123@clickhouse:8123/ecommerce_dwh"
SCHEMA = "ecommerce_dwh"


def request(path, method="GET", payload=None, token=None):
    headers = {}
    data = None
    if token:
        headers["Authorization"] = f"Bearer {token}"
    if payload is not None:
        headers["Content-Type"] = "application/json"
        data = json.dumps(payload).encode()
    req = urllib.request.Request(
        f"{BASE_URL}{path}",
        data=data,
        headers=headers,
        method=method,
    )
    try:
        with urllib.request.urlopen(req) as response:
            body = response.read().decode()
            return json.loads(body) if body else {}
    except urllib.error.HTTPError as exc:
        error_body = exc.read().decode()
        print(f"Superset API error {exc.code} for {path}: {error_body}", file=sys.stderr)
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
        payload={
            "username": USERNAME,
            "password": PASSWORD,
            "provider": "db",
            "refresh": True,
        },
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
        print(f"Database exists: id={existing['id']}")
        return existing["id"]
    created = request(
        "/api/v1/database/",
        method="POST",
        payload={
            "database_name": DATABASE_NAME,
            "sqlalchemy_uri": SQLALCHEMY_URI,
            "expose_in_sqllab": True,
        },
        token=token,
    )
    database_id = created.get("id") or created["result"]["id"]
    print(f"Database created: id={database_id}")
    return database_id


def ensure_dataset(token, database_id, schema, table_name):
    datasets = request("/api/v1/dataset/?q=(page:0,page_size:200)", token=token)["result"]
    for dataset in datasets:
        if (
            dataset.get("table_name") == table_name
            and dataset.get("schema") == schema
            and dataset.get("database", {}).get("id") == database_id
        ):
            request(f"/api/v1/dataset/{dataset['id']}", method="DELETE", token=token)
            print(f"Dataset recreated: removed stale {schema}.{table_name} id={dataset['id']}")
            break
    created = request(
        "/api/v1/dataset/",
        method="POST",
        payload={
            "database": database_id,
            "schema": schema,
            "table_name": table_name,
        },
        token=token,
    )
    dataset_id = created.get("id") or created["result"]["id"]
    print(f"Dataset created: {schema}.{table_name} -> id={dataset_id}")
    return dataset_id


def ensure_chart(token, title, dataset_id, viz_type, params, query_context):
    charts = request("/api/v1/chart/?q=(page:0,page_size:200)", token=token)["result"]
    existing = find_by(charts, "slice_name", title)
    if existing:
        db_path = "/app/superset_home/superset.db"
        connection = sqlite3.connect(db_path)
        cursor = connection.cursor()
        cursor.execute(
            """
            UPDATE slices
            SET slice_name = ?, datasource_id = ?, datasource_type = ?, viz_type = ?, params = ?, query_context = ?
            WHERE id = ?
            """,
            (
                title,
                dataset_id,
                "table",
                viz_type,
                json.dumps(params),
                json.dumps(query_context),
                existing["id"],
            ),
        )
        connection.commit()
        connection.close()
        print(f"Chart updated: {title} -> id={existing['id']}")
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
    chart_id = created.get("id") or created["result"]["id"]
    print(f"Chart created: {title} -> id={chart_id}")
    return chart_id


def ensure_dashboard(token, title):
    dashboards = request("/api/v1/dashboard/?q=(page:0,page_size:100)", token=token)["result"]
    existing = find_by(dashboards, "dashboard_title", title)
    if existing:
        print(f"Dashboard exists: {title} -> id={existing['id']}")
        return existing["id"]
    created = request(
        "/api/v1/dashboard/",
        method="POST",
        payload={
            "dashboard_title": title,
            "published": True,
        },
        token=token,
    )
    dashboard_id = created.get("id") or created["result"]["id"]
    print(f"Dashboard created: {title} -> id={dashboard_id}")
    return dashboard_id


def wire_dashboard_layout(dashboard_id, chart_ids):
    db_path = "/app/superset_home/superset.db"
    connection = sqlite3.connect(db_path)
    cursor = connection.cursor()
    cursor.execute("DELETE FROM dashboard_slices WHERE dashboard_id = ?", (dashboard_id,))
    for chart_id in chart_ids:
        cursor.execute(
            "INSERT INTO dashboard_slices (dashboard_id, slice_id) VALUES (?, ?)",
            (dashboard_id, chart_id),
        )
    cursor.execute(
        "UPDATE dashboards SET position_json = NULL, json_metadata = ? WHERE id = ?",
        (json.dumps({}), dashboard_id),
    )
    connection.commit()
    connection.close()
    print(f"Dashboard linked with {len(chart_ids)} charts: id={dashboard_id}")


def metric(sql_expression, label):
    return {
        "expressionType": "SQL",
        "sqlExpression": sql_expression,
        "label": label,
        "optionName": label,
    }


def build_timeseries_chart(token, title, dataset_id, date_column, metric_def, groupby):
    params = {
        "datasource": f"{dataset_id}__table",
        "viz_type": "echarts_timeseries_line",
        "x_axis": date_column,
        "metrics": [metric_def],
        "groupby": groupby,
        "row_limit": 1000,
        "time_range": "No filter",
    }
    query_context = {
        "datasource": {"id": dataset_id, "type": "table"},
        "queries": [
            {
                "time_range": "No filter",
                "granularity": date_column,
                "columns": groupby,
                "metrics": [metric_def],
                "orderby": [[metric_def, False]],
                "row_limit": 1000,
                "is_timeseries": True,
            }
        ],
        "form_data": params,
        "result_format": "json",
        "result_type": "full",
    }
    return ensure_chart(token, title, dataset_id, "echarts_timeseries_line", params, query_context)


def build_dist_bar_chart(token, title, dataset_id, groupby, metric_defs):
    params = {
        "datasource": f"{dataset_id}__table",
        "viz_type": "dist_bar",
        "groupby": groupby,
        "metrics": metric_defs,
        "row_limit": 100,
        "time_range": "No filter",
    }
    query_context = {
        "datasource": {"id": dataset_id, "type": "table"},
        "queries": [
            {
                "time_range": "No filter",
                "columns": groupby,
                "metrics": metric_defs,
                "orderby": [[metric_defs[0], False]],
                "row_limit": 100,
                "is_timeseries": False,
            }
        ],
        "form_data": params,
        "result_format": "json",
        "result_type": "full",
    }
    return ensure_chart(token, title, dataset_id, "dist_bar", params, query_context)


def build_table_chart(token, title, dataset_id, groupby, metric_defs):
    params = {
        "datasource": f"{dataset_id}__table",
        "viz_type": "table",
        "groupby": groupby,
        "metrics": metric_defs,
        "row_limit": 50,
        "order_desc": True,
        "time_range": "No filter",
    }
    query_context = {
        "datasource": {"id": dataset_id, "type": "table"},
        "queries": [
            {
                "time_range": "No filter",
                "columns": groupby,
                "metrics": metric_defs,
                "orderby": [[metric_defs[0], False]] if metric_defs else [],
                "row_limit": 50,
                "is_timeseries": False,
            }
        ],
        "form_data": params,
        "result_format": "json",
        "result_type": "full",
    }
    return ensure_chart(token, title, dataset_id, "table", params, query_context)


def safe_dataset(token, database_id, table_name):
    try:
        return ensure_dataset(token, database_id, SCHEMA, table_name)
    except Exception as exc:
        print(f"SKIP dataset {table_name}: {exc}", file=sys.stderr)
        return None


wait_until_healthy()
token = login()
database_id = ensure_database(token)

mart_daily_id = safe_dataset(token, database_id, "mart_daily_sessions")
mart_device_id = safe_dataset(token, database_id, "mart_events_by_device")
raw_id = safe_dataset(token, database_id, "raw_ecommerce_events")
fct_id = safe_dataset(token, database_id, "fct_events")

chart_ids = []

if mart_daily_id:
    chart_ids.append(
        build_timeseries_chart(
            token,
            "Daily events (sum across sessions)",
            mart_daily_id,
            "event_date",
            metric("sum(event_count)", "events"),
            [],
        )
    )
    chart_ids.append(
        build_timeseries_chart(
            token,
            "Daily errors",
            mart_daily_id,
            "event_date",
            metric("sum(error_events)", "errors"),
            [],
        )
    )
    chart_ids.append(
        build_table_chart(
            token,
            "Sessions: events and errors",
            mart_daily_id,
            ["event_date", "session_id"],
            [
                metric("sum(event_count)", "event_count"),
                metric("sum(error_events)", "error_events"),
            ],
        )
    )

if mart_device_id:
    chart_ids.append(
        build_dist_bar_chart(
            token,
            "Events by device",
            mart_device_id,
            ["device"],
            [metric("sum(event_count)", "events")],
        )
    )
    chart_ids.append(
        build_dist_bar_chart(
            token,
            "Events by type (mart)",
            mart_device_id,
            ["event"],
            [metric("sum(event_count)", "events")],
        )
    )

if raw_id:
    chart_ids.append(
        build_dist_bar_chart(
            token,
            "Event types (raw)",
            raw_id,
            ["event"],
            [metric("count()", "cnt")],
        )
    )
    chart_ids.append(
        build_dist_bar_chart(
            token,
            "Devices (raw)",
            raw_id,
            ["device"],
            [metric("count()", "cnt")],
        )
    )

if fct_id:
    chart_ids.append(
        build_table_chart(
            token,
            "Fact table: events sample",
            fct_id,
            ["event_date", "event", "user_id"],
            [metric("count()", "rows")],
        )
    )

dashboard_id = ensure_dashboard(token, "Ecommerce Analytics")
if chart_ids:
    wire_dashboard_layout(dashboard_id, chart_ids)
else:
    print(
        "No charts: ClickHouse tables are empty — run the DAG and restart the superset container",
        file=sys.stderr,
    )

print("Superset bootstrap completed")
PY
