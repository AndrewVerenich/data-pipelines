# Data Pipelines

Коллекция production-ready data pipelines, демонстрирующих современные подходы к построению масштабируемых систем обработки данных

Этот репозиторий содержит реализованные data pipelines с использованием различных технологических стеков. Каждый пайплайн является полноценным решением для конкретных use cases в области Data Engineering.

## 🎯 Цель проекта

Демонстрация экспертизы в области Data Engineering:
- Архитектурное проектирование data pipelines
- Работа со streaming данными
- Интеграция различных систем хранения данных
- Мониторинг и визуализация метрик
- Real-time data processing
- CDC (Change Data Capture)
- ETL/ELT процессы

## 🚀 Доступные пайплайны

### 📈 [Marketing Analytics Platform](./marketing-analytics-platform/README.md)

**Стек:** ClickHouse • Apache Kafka • Kotlin / Spring Boot • Apache Superset

Real-time / batch Data Warehouse для маркетинговой аналитики: ingestion событий из Kafka, dimension modeling (star schema, SCD Type 2), инкрементальная агрегация и маркетинговые метрики.

**Ключевые возможности:**
- ✅ Многослойная DWH: Raw → Dimension → Fact → Aggregated
- ✅ Ingestion из Kafka через Kafka Engine + Materialized Views
- ✅ Star schema с dimension tables (SCD Type 2)
- ✅ SummingMergeTree / AggregatingMergeTree, Projections, Data Skipping Indexes
- ✅ Метрики: DAU, MAU, Conversion Rate, CAC, ROAS, ARPU, LTV
- ✅ Генерация событий (website / ad / backend) на Kotlin + Spring Boot
- ✅ BI-дашборды в Apache Superset
- ✅ Historical backfill для наглядных графиков

---

### 🏦 [Fintech ELT Data Lakehouse Pipeline](./fintech-lakehouse-analytics/README.md)

**Стек:** Postgres • Debezium • Apache Kafka • ClickHouse • dbt • Apache Airflow • Apache Superset

Production-like ELT-пайплайн для финтех-аналитики: от CDC-захвата таблиц Postgres до бизнес-витрин в ClickHouse с
оркестрацией в Airflow и дашбордами в Superset. star schema с surrogate keys, CDC-дедупликация и витрины с
финтех-метриками.

**Ключевые возможности:**
- ✅ CDC ingestion из Postgres через Debezium + Kafka
- ✅ Real-time загрузка в ClickHouse через Kafka Engine + Materialized Views
- ✅ Многослойное моделирование в dbt: staging → intermediate → dimensions → facts → marts
- ✅ Star schema с surrogate keys и CDC-дедупликацией в staging
- ✅ бизнес-витрины: CLV, RFM-сегментация, когортная retention, fraud indicators и др.
- ✅ Последовательный Airflow DAG: pre-checks → seed → layers runs → test
- ✅ Автоматический bootstrap Superset: дашборд «Fintech Analytics»

---

### 📊 [IoT Real-Time Analytics Pipeline](./iot-pipeline/README.md)

**Стек:** PostgreSQL • Debezium • Apache Kafka • Kafka Streams • ClickHouse • Grafana

Комплексный пайплайн для real-time обработки IoT данных с визуализацией метрик.

**Ключевые возможности:**
- ✅ CDC с PostgreSQL через Debezium (WAL-based)
- ✅ Stream processing на Kafka Streams
- ✅ Columnar storage в ClickHouse для аналитики
- ✅ Real-time визуализация в Grafana
- ✅ Автоматическая агрегация по временным окнам

---

### 🛒 [Ecommerce Big Data Analytics Pipeline](./ecommerce-batch-pipeline/README.md)

**Стек:** Hadoop • Spark • Livy • Airflow • PostgreSQL • Superset

Комплексный пайплайн для batch‑обработки e‑commerce логов с построением аналитических дашбордов.

**Ключевые возможности:**
- ✅ Хранение данных в HDFS (Hadoop NameNode + DataNodes)
- ✅ Batch‑обработка логов в Apache Spark
- ✅ Оркестрация ETL‑процессов через Apache Airflow
- ✅ REST API для Spark через Apache Livy
- ✅ Хранение результатов в PostgreSQL
- ✅ BI‑дашборды и визуализация в Apache Superset

---

### ⚡ [Flink User Behaviour Analytics Pipeline](./user-behaviour-pipeline/README.md)

**Стек:** Apache Kafka • Apache Flink • ClickHouse • Grafana • Spring Boot WebSocket Gateway

Комплексный пайплайн для real-time обработки пользовательских событий с визуализацией метрик.

**Ключевые возможности:**
- ✅ Потоковая обработка событий в Apache Flink
- ✅ Хранение агрегированных метрик в ClickHouse
- ✅ Визуализация данных в Grafana
- ✅ Kafka как брокер сообщений для событий
- ✅ Эмуляция пользовательского поведения через WebSocket Gateway
- ✅ Docker Compose для оркестрации всех компонентов


