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
DATABASE_NAME = "Trino Banking Lakehouse"
SQLALCHEMY_URI = "trino://trino@trino:8080/iceberg/gold"
SCHEMA = "gold"


def request(path, method="GET", payload=None, token=None):
    headers = {}
    data = None
    if token:
        headers["Authorization"] = f"Bearer {token}"
    if payload is not None:
        headers["Content-Type"] = "application/json"
        data = json.dumps(payload).encode("utf-8")

    req = urllib.request.Request(
        f"{BASE_URL}{path}",
        data=data,
        headers=headers,
        method=method,
    )

    try:
        with urllib.request.urlopen(req) as response:
            body = response.read().decode("utf-8")
            return json.loads(body) if body else {}
    except urllib.error.HTTPError as exc:
        print(f"Superset API error {exc.code} for {path}: {exc.read().decode('utf-8')}", file=sys.stderr)
        raise


def wait_until_healthy():
    for _ in range(90):
        try:
            with urllib.request.urlopen(f"{BASE_URL}/health", timeout=5) as response:
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
        return existing["id"]

    created = request(
        "/api/v1/database/",
        method="POST",
        payload={"database_name": DATABASE_NAME, "sqlalchemy_uri": SQLALCHEMY_URI},
        token=token,
    )
    return created.get("id") or created["result"]["id"]


def ensure_dataset(token, database_id, schema, table_name):
    datasets = request("/api/v1/dataset/?q=(page:0,page_size:500)", token=token)["result"]
    for dataset in datasets:
        if (
            dataset.get("table_name") == table_name
            and dataset.get("schema") == schema
            and dataset.get("database", {}).get("id") == database_id
        ):
            request(f"/api/v1/dataset/{dataset['id']}", method="DELETE", token=token)
            break

    created = request(
        "/api/v1/dataset/",
        method="POST",
        payload={"database": database_id, "schema": schema, "table_name": table_name},
        token=token,
    )
    return created.get("id") or created["result"]["id"]


def ensure_chart(token, title, dataset_id, viz_type, params, query_context):
    charts = request("/api/v1/chart/?q=(page:0,page_size:500)", token=token)["result"]
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


def wire_dashboard_layout(dashboard_id, chart_ids):
    connection = sqlite3.connect("/app/superset_home/superset.db")
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


def metric(sql_expression, label):
    return {
        "expressionType": "SQL",
        "sqlExpression": sql_expression,
        "label": label,
        "optionName": label,
    }


def build_timeseries_chart(token, title, dataset_id, date_column, metric_defs, groupby):
    params = {
        "datasource": f"{dataset_id}__table",
        "viz_type": "echarts_timeseries_line",
        "x_axis": date_column,
        "metrics": metric_defs,
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
                "metrics": metric_defs,
                "orderby": [[metric_defs[0], False]] if metric_defs else [],
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
                "orderby": [[metric_defs[0], False]] if metric_defs else [],
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


wait_until_healthy()
token = login()
database_id = ensure_database(token)

datasets = {
    "spending_by_category": ensure_dataset(token, database_id, SCHEMA, "spending_by_category"),
    "customer_segments": ensure_dataset(token, database_id, SCHEMA, "customer_segments"),
    "anomaly_flags": ensure_dataset(token, database_id, SCHEMA, "anomaly_flags"),
    "monthly_cashflow": ensure_dataset(token, database_id, SCHEMA, "monthly_cashflow"),
    "channel_analysis": ensure_dataset(token, database_id, SCHEMA, "channel_analysis"),
}

chart_ids = []
chart_ids.append(
    build_dist_bar_chart(
        token,
        "Расходы по категориям (USD)",
        datasets["spending_by_category"],
        ["category"],
        [metric("sum(total_amount)", "total_amount")],
    )
)
chart_ids.append(
    build_timeseries_chart(
        token,
        "Тренд расходов по месяцам",
        datasets["spending_by_category"],
        "month",
        [metric("sum(total_amount)", "total_amount")],
        ["category"],
    )
)
chart_ids.append(
    build_dist_bar_chart(
        token,
        "RFM: клиенты по сегменту",
        datasets["customer_segments"],
        ["segment"],
        [metric("count(customer_id)", "customers")],
    )
)
chart_ids.append(
    build_table_chart(
        token,
        "Подозрительные транзакции",
        datasets["anomaly_flags"],
        ["transaction_id", "account_id", "anomaly_reason"],
        [metric("max(amount)", "amount")],
    )
)
chart_ids.append(
    build_timeseries_chart(
        token,
        "Денежный поток по месяцам",
        datasets["monthly_cashflow"],
        "month",
        [
            metric("sum(total_credit)", "total_credit"),
            metric("sum(total_debit)", "total_debit"),
            metric("sum(net_cashflow)", "net_cashflow"),
        ],
        [],
    )
)
chart_ids.append(
    build_table_chart(
        token,
        "Cashflow: топ клиентов",
        datasets["monthly_cashflow"],
        ["customer_id"],
        [metric("sum(net_cashflow)", "net_cashflow"), metric("sum(total_credit)", "total_credit")],
    )
)
chart_ids.append(
    build_dist_bar_chart(
        token,
        "Анализ каналов",
        datasets["channel_analysis"],
        ["channel"],
        [metric("sum(total_amount)", "total_amount"), metric("sum(tx_count)", "tx_count")],
    )
)
chart_ids.append(
    build_timeseries_chart(
        token,
        "Динамика каналов",
        datasets["channel_analysis"],
        "month",
        [metric("sum(tx_count)", "tx_count")],
        ["channel"],
    )
)

dashboard_id = ensure_dashboard(token, "Banking Analytics")
wire_dashboard_layout(dashboard_id, chart_ids)

print("Superset bootstrap completed")
PY
