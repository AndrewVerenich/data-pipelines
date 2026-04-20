# Data Pipelines

Коллекция **production-like** пайплайнов: каждый проект в репозитории — это законченный контур «данные → хранилище → трансформации → оркестрация (где нужно) → визуализация», собранный в **Docker Compose** и приближенный к тому, как подобные системы выглядят в продуктовой среде.

Здесь намеренно сочетаются **разные архитектурные акценты**:
- **Lakehouse / ELT** ([Fintech](./fintech-lakehouse-analytics/README.md)): CDC из OLTP, колоночное хранилище, **dbt**-слои и витрины, **Airflow**, **Superset**.
- **Real-time DWH из Kafka** ([Marketing](./marketing-analytics-platform/README.md)): события в Kafka, ClickHouse, star schema и предагрегации, Superset.
- **Smart Home IoT** ([IoT](./iot-pipeline/README.md)): Kotlin-симулятор (физика комнат, REST-конфиг в Postgres), Debezium CDC в Kafka Streams (KTable), топологии климат / освещение / охрана, **Kafka → ClickHouse → Grafana** (в т.ч. HVAC и lighting commands).
- **Batch DWH + ELT** ([Ecommerce](./ecommerce-batch-pipeline/README.md)): **Medallion**-слои на практике — **Spark**: bronze / silver (Parquet в HDFS) → load в ClickHouse (raw); **«золото»** — **dbt** (staging → dimensions → facts → marts, схема «звезда»), **Airflow** (Livy + dbt в Docker), **Superset** на ClickHouse.
- **S3 Data Lakehouse** ([Banking](./banking-lakehouse/README.md)): Kafka → MinIO (S3) → **Spark + Iceberg** (Bronze / Silver / Gold), SQL через **Trino**, **Airflow**, **Superset**.
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

### 🏠 [Smart Home IoT Pipeline](./iot-pipeline/README.md)

**Стек:** Kotlin / Spring Boot • PostgreSQL • Debezium • Apache Kafka • Kafka Streams • ClickHouse • Grafana

Потоковый контур **умного дома**: замкнутая петля «стримы → команды HVAC/света → физика комнаты → новые датчики»; 
конфигурация комнат — **SSOT в Postgres** с **CDC** в **KTable** стримов; выбранные топики пишутся в **ClickHouse** 
(Kafka Engine + MV) и отображаются на дашборде **Grafana**. JVM-сервисы собираются **локально** (`bootJar`), Docker копирует готовые JAR.

**Ключевые возможности:**
- ✅ Симулятор: датчики (температура, влажность, движение, lux, дверь/окно), актуаторы `command.hvac` / `command.lighting`, REST `GET`/`PATCH /api/rooms`
- ✅ Kafka Streams: окна и suppress для климата, join KStream × KTable, освещение (motion × lux), merge тревог 
- ✅ Interactive Queries (REST) по последнему состоянию HVAC
- ✅ ClickHouse: MergeTree-витрины для температуры, влажности, команд HVAC и **освещения**, аналитики климата, охраны
- ✅ Grafana 10: дашборд с time series и таблицей алертов

---

### 🛒 [Ecommerce batch DWH](./ecommerce-batch-pipeline/README.md)

**Стек:** Hadoop HDFS • Apache Spark • Apache Livy • ClickHouse • dbt (dbt-clickhouse) • Apache Airflow • Apache Superset

Batch‑контур e‑commerce: синтетические события и справочники в **HDFS** → **Spark** (этапы `bronze` → `silver` → `load_ch`, идемпотентность по `ingest_batch_id`) → сырой слой в **ClickHouse** → **dbt** (измерения, факт кликстрима, витрины; **схема «звезда»**: `fct_events`, `dim_user`, `dim_product`) → **Airflow** (три запуска Spark через Livy, затем слои dbt и `dbt test`) → **Superset** на ClickHouse. DWH — только ClickHouse; PostgreSQL в compose используется лишь как БД метаданных Airflow (`airflow-db`).

**Ключевые возможности:**
- ✅ Bronze / silver на HDFS (Parquet), join к справочникам пользователей и товаров в silver
- ✅ Загрузка raw в ClickHouse по JDBC, партиционирование по батчу для повторных прогонов
- ✅ Многослойное моделирование в dbt: staging → intermediate → dimensions → facts → marts
- ✅ Dimensional modeling: факт на зерно «одно событие», суррогатные ключи, демо SCD2 на seed
- ✅ DAG `ecommerce_dwh_pipeline`: проверки, Spark ×3, dbt по тегам, тесты качества
- ✅ Superset с подключением к ClickHouse (`clickhouse-connect`)
- ✅ Диаграммы пайплайна и моделирования измерений в README проекта

---

### 🏗️ [Banking Transactions Lakehouse](./banking-lakehouse/README.md)

**Стек:** Apache Kafka • Kafka Connect S3 Sink • MinIO (S3) • Apache Spark • Apache Iceberg • Apache Trino • Apache Airflow • Apache Superset

S3 Data Lakehouse для банковских транзакций: ingestion через Kafka Connect S3 Sink в MinIO, Medallion-слои (Bronze → Silver → Gold) на Apache Iceberg, SQL-запросы через Trino, оркестрация Airflow, визуализация в Superset.

**Ключевые возможности:**
- ✅ MinIO как S3-совместимый Data Lake (Bronze / Silver / Gold)
- ✅ Apache Iceberg: ACID-транзакции, schema evolution, time travel
- ✅ Kafka Connect S3 Sink Connector для автоматической записи в MinIO
- ✅ Apache Spark для ETL: очистка, дедупликация, Iceberg-таблицы
- ✅ Apache Trino для SQL-запросов по Iceberg-таблицам на MinIO
- ✅ Medallion Architecture на объектном хранилище
- ✅ Пять аналитических витрин: расходы, RFM-сегментация, аномалии, cashflow, каналы
- ✅ Airflow DAG с послойным запуском Spark и data quality checks
- ✅ Автоматический bootstrap Superset: дашборд «Banking Analytics»

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


