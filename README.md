# Data Pipelines

Коллекция **production-like** пайплайнов: каждый проект в репозитории — это законченный контур «данные → хранилище → трансформации → оркестрация (где нужно) → визуализация», собранный в **Docker Compose** и приближенный к тому, как подобные системы выглядят в продуктовой среде.

Здесь намеренно сочетаются **разные архитектурные акценты**:
- **Lakehouse / ELT** ([Fintech](./fintech-lakehouse-analytics/README.md)): CDC из OLTP, колоночное хранилище, **dbt**-слои и витрины, **Airflow**, **Superset**.
- **Real-time DWH из Kafka** ([Marketing](./marketing-analytics-platform/README.md)): события в Kafka, ClickHouse, star schema и предагрегации, Superset.
- **Smart Home IoT** ([IoT](./iot-pipeline/README.md)): симулятор датчиков/актуаторов, Debezium CDC конфигурации, Kafka Streams (климат, свет, охрана), ClickHouse, Grafana.
- **Классический batch** ([Ecommerce](./ecommerce-batch-pipeline/README.md)): HDFS, Spark, Livy, Airflow, PostgreSQL, Superset.
- **Стриминговая обработка** ([User Behaviour](./user-behaviour-pipeline/README.md)): Flink, ClickHouse, Grafana.

## 🎯 Цель проекта

Демонстрация **инженерных компетенций в Data Engineering** — от схемы данных и ingestion до воспроизводимых витрин и 
дашбордов:

- **Архитектура пайплайнов**: разделение слоёв (raw / staging / core / marts), паттерны Kafka Engine + Materialized Views в ClickHouse, сравнение batch и streaming.
- **Качество и моделирование**: dimensional modeling (star schema), surrogate keys, тесты на гранях данных (**dbt** в fintech), дедупликация CDC-событий в staging.
- **Интеграция систем**: OLTP + CDC (**Debezium**), брокеры (**Kafka**), OLAP (**ClickHouse**), оркестрация (**Airflow**), BI (**Superset**, **Grafana**).
- **ELT vs ETL**: трансформации в аналитическом хранилище (dbt + ClickHouse) напротив вынесенной обработки (Spark, Flink).
- **Наблюдаемость и потребление**: бизнес-метрики в дашбордах, явные URL и учётные данные для локального запуска, документация по домену в README каждого проекта.

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

Production-like **ELT lakehouse** для финтех-домена: CDC семи таблиц Postgres → Kafka → ClickHouse, затем **dbt** (26 моделей, тесты на ключевых гранях) → витрины → **Airflow** (послойный запуск и quality gate) → **Superset** (дашборд и чарты из коробки).

**Ключевые возможности:**
- ✅ CDC ingestion из Postgres через Debezium + Kafka (7 таблиц)
- ✅ Загрузка в ClickHouse через Kafka Engine + Materialized Views
- ✅ Многослойное моделирование в dbt: staging → intermediate → dimensions → facts → marts
- ✅ Star schema с surrogate keys и CDC-дедупликацией в staging
- ✅ Восемь бизнес-витрин: CLV, RFM, когортная retention, здоровье кредитного портфеля, fraud indicators и др.
- ✅ Последовательный Airflow DAG: pre-checks → seed → прогон по тегам слоёв → `dbt test`
- ✅ Автоматический bootstrap Apache Superset: дашборд «Fintech Analytics»

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


