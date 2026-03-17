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
DATABASE_NAME = "ClickHouse Marketing"
SQLALCHEMY_URI = "clickhousedb://admin:admin123@clickhouse:8123/marketing"


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
        },
        token=token,
    )
    database_id = created.get("id") or created["result"]["id"]
    print(f"Database created: id={database_id}")
    return database_id


def ensure_dataset(token, database_id, schema, table_name):
    datasets = request("/api/v1/dataset/?q=(page:0,page_size:100)", token=token)["result"]
    for dataset in datasets:
        if (
            dataset.get("table_name") == table_name
            and dataset.get("schema") == schema
            and dataset.get("database", {}).get("id") == database_id
        ):
            print(f"Dataset exists: {schema}.{table_name} -> id={dataset['id']}")
            return dataset["id"]

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
    print(f"Dashboard linked with {len(chart_ids)} charts: id={dashboard_id}")


def metric(sql_expression, label):
    return {
        "expressionType": "SQL",
        "sqlExpression": sql_expression,
        "label": label,
        "optionName": label,
    }


def build_timeseries_chart(title, dataset_id, date_column, metric_def, groupby):
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


def build_multi_metric_timeseries_chart(title, dataset_id, date_column, metric_defs):
    params = {
        "datasource": f"{dataset_id}__table",
        "viz_type": "echarts_timeseries_line",
        "x_axis": date_column,
        "metrics": metric_defs,
        "groupby": [],
        "row_limit": 1000,
        "time_range": "No filter",
    }
    query_context = {
        "datasource": {"id": dataset_id, "type": "table"},
        "queries": [
            {
                "time_range": "No filter",
                "granularity": date_column,
                "columns": [],
                "metrics": metric_defs,
                "orderby": [[metric_defs[0], False]],
                "row_limit": 1000,
                "is_timeseries": True,
            }
        ],
        "form_data": params,
        "result_format": "json",
        "result_type": "full",
    }
    return ensure_chart(token, title, dataset_id, "echarts_timeseries_line", params, query_context)


def build_table_chart(title, dataset_id, groupby, metric_defs):
    params = {
        "datasource": f"{dataset_id}__table",
        "viz_type": "table",
        "groupby": groupby,
        "metrics": metric_defs,
        "row_limit": 20,
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
                "orderby": [[metric_defs[0], False]],
                "row_limit": 20,
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

daily_user_activity_id = ensure_dataset(token, database_id, "marketing", "daily_user_activity")
daily_active_users_by_source_id = ensure_dataset(token, database_id, "marketing", "daily_active_users_by_source")
campaign_performance_id = ensure_dataset(token, database_id, "marketing", "campaign_performance_daily")
# Только представления: базовые таблицы содержат AggregateFunction(uniq) → JDBC отдаёт Bitmap, клиент падает
conversion_funnel_merged_id = ensure_dataset(token, database_id, "marketing", "conversion_funnel_daily_merged")
user_ltv_final_id = ensure_dataset(token, database_id, "marketing", "user_ltv_final")

revenue_chart_id = build_timeseries_chart(
    "Daily Revenue by Source",
    daily_user_activity_id,
    "event_date",
    metric("sum(total_revenue)", "sum_total_revenue"),
    ["event_source"],
)
active_users_chart_id = build_timeseries_chart(
    "Daily Active Users by Source",
    daily_active_users_by_source_id,
    "event_date",
    metric("unique_users", "unique_users"),
    ["event_source"],
)
funnel_chart_id = build_multi_metric_timeseries_chart(
    "Conversion Funnel by Stage",
    conversion_funnel_merged_id,
    "event_date",
    [
        metric("page_viewers", "page_viewers"),
        metric("clickers", "clickers"),
        metric("cart_adders", "cart_adders"),
        metric("purchasers", "purchasers"),
    ],
)
campaign_roas_chart_id = build_table_chart(
    "Top Campaigns by ROAS",
    campaign_performance_id,
    ["campaign_id", "platform"],
    [
        metric("if(sum(total_cost) = 0, 0, sum(total_revenue) / sum(total_cost))", "roas"),
        metric("sum(total_revenue)", "sum_total_revenue"),
    ],
)
top_users_chart_id = build_table_chart(
    "Top Users by LTV",
    user_ltv_final_id,
    ["user_id"],
    [metric("total_revenue", "total_revenue")],
)

dashboard_id = ensure_dashboard(token, "Marketing Analytics Overview")
wire_dashboard_layout(
    dashboard_id,
    [
        revenue_chart_id,
        active_users_chart_id,
        funnel_chart_id,
        campaign_roas_chart_id,
        top_users_chart_id,
    ],
)
print("Superset bootstrap completed")
PY
