# Fintech Lakehouse Analytics

**Схема:**

```mermaid
flowchart LR
  subgraph oltp ["Postgres OLTP"]
    Tbls["customers · accounts · merchants<br/>transactions · loans · loan_payments<br/>exchange_rates"]
  end

  subgraph cdc ["CDC"]
    Deb["Debezium Connect"]
  end

  subgraph kafka ["Kafka"]
    Topics["fintech.public.*<br/>(7 topics)"]
  end

  subgraph ch ["ClickHouse"]
    subgraph rawCh ["Raw Layer"]
      RK["raw_* Kafka Engine"]
      MV["mv_* Materialized Views"]
    end
    subgraph landCh ["Landing (MergeTree)"]
      Land["customers · accounts · merchants<br/>transactions · loans<br/>loan_payments · exchange_rates"]
    end
  end

  subgraph dbt ["dbt Transforms"]
    Stg["staging (7 views)"]
    Int["intermediate (3 views)"]
    Dim["dimensions (5 views)"]
    Fct["facts (3 tables)"]
    Mart["marts (8 tables)"]
  end

  subgraph orch ["Orchestration"]
    Af["Airflow DAG"]
  end

  subgraph bi ["BI"]
    Ss["Apache Superset<br/>8 charts · 1 dashboard"]
  end

  Tbls --> Deb --> Topics --> RK --> MV --> Land
  Land --> Stg --> Int --> Dim
  Int --> Fct
  Dim --> Fct
  Fct --> Mart
  Af --> dbt
  Mart --> Ss
```

ELT-пайплайн для финтех-аналитики: `Postgres → Debezium → Kafka → ClickHouse → dbt → Airflow → Superset`.

Решает задачу построения единой аналитической платформы поверх транзакционной БД: от CDC-захвата операций до бизнес-витрин с метриками по выручке, клиентам, платёжным каналам, кредитному портфелю и фроду.

**Ключевые возможности:**
- ✅ CDC ingestion из Postgres через Debezium + Kafka (7 таблиц, логическая репликация)
- ✅ Real-time загрузка в ClickHouse через Kafka Engine + Materialized Views
- ✅ Многослойное моделирование в dbt: staging → intermediate → dimensions → facts → marts
- ✅ Star schema с surrogate keys и CDC-дедупликацией в staging
- ✅ 8 бизнес-витрин: CLV, RFM-сегментация, когортная retention, fraud indicators и др.
- ✅ Последовательный Airflow DAG: pre-checks → seed → 5 layer runs → test
- ✅ Автоматический bootstrap Superset: подключение к ClickHouse, 8 чартов, дашборд
- ✅ Полный Docker Compose с 12 сервисами, healthchecks и depends_on

---

## 🛠 Технологический стек

| Компонент | Технология | Описание |
|-----------|-----------|----------|
| **OLTP-источник** | PostgreSQL 15 | Транзакционная БД с `wal_level=logical` |
| **CDC** | Debezium 2.5 | Захват изменений из WAL, публикация в Kafka |
| **Брокер сообщений** | Apache Kafka (Confluent 7.5) | Топики `fintech.public.*` |
| **Аналитическая БД** | ClickHouse 23.8 | Columnar OLAP-хранилище |
| **Трансформации** | dbt-core 1.10 + dbt-clickhouse | 26 моделей, 37 тестов, 4 макроса |
| **Оркестрация** | Apache Airflow 2.8 | DAG с DockerOperator для dbt |
| **BI / Дашборды** | Apache Superset 3.1 | Визуализация витрин |
| **Инфраструктура** | Docker Compose | 12 сервисов, 3 volumes, единая сеть |

---

## 🔧 Требования

- **Docker** 20.10+
- **Docker Compose** 2.0+

---

## 🚀 Быстрый старт

```bash
cd fintech-lakehouse-analytics
docker-compose up -d --build
```

**Что произойдёт автоматически:**
1. Поднимутся Postgres, Kafka, Zookeeper, ClickHouse
2. `debezium-init` зарегистрирует CDC-коннектор
3. `data-loader` заполнит Postgres тестовыми данными (через CDC → Kafka → ClickHouse)
4. `dbt` выполнит `dbt deps && dbt run` — построит все слои в ClickHouse
5. `superset` поднимет сервер и bootstrap-скрипт создаст дашборд с чартами
6. `airflow` запустит DAG `fintech_dbt_layered_hourly` по расписанию `@hourly`

Ручной запуск dbt (опционально):

```bash
docker compose run --rm dbt deps
docker compose run --rm dbt seed
docker compose run --rm dbt run
docker compose run --rm dbt test
```

Остановка сервисов:

```bash
# Остановка с сохранением данных
docker-compose stop

# Остановка с удалением контейнеров (данные сохраняются в volumes)
docker-compose down

# Полное удаление включая volumes (⚠️ удалит все данные)
docker-compose down -v
```

---

## 🌐 URL сервисов

| Сервис | URL | Credentials | Описание |
|--------|-----|-------------|----------|
| **Postgres** | `localhost:5432` | demo / demo | OLTP-источник |
| **Debezium** | http://localhost:8083 | — | REST API коннекторов |
| **Kafka UI** | http://localhost:8088 | — | Мониторинг топиков |
| **ClickHouse HTTP** | http://localhost:8123 | admin / admin123 | HTTP-интерфейс |
| **Airflow** | http://localhost:8081 | admin / admin | Оркестрация DAG |
| **Superset** | http://localhost:8089 | admin / admin | BI-дашборды |
| **dbt Docs** | http://localhost:8082 | — | Документация и lineage |

---

## 📁 Структура проекта

```
fintech-lakehouse-analytics/
├── docker-compose.yml
├── README.md
├── docs/
│   ├── dag.png                    # Скриншот Airflow DAG
│   ├── lineage.png                # Скриншот dbt lineage
│   └── superset.png               # Скриншот Superset dashboard
├── postgres/
│   ├── init.sql                   # DDL: 7 таблиц (customers, accounts, merchants, …)
│   └── seed_after_cdc.sql         # INSERT: тестовые данные (200 клиентов, 50K транзакций, …)
├── debezium/
│   └── init-debezium.sh           # Регистрация Postgres-коннектора (7 таблиц)
├── clickhouse/
│   └── clickhouse-init.sql        # Kafka Engine + MergeTree + Materialized Views
├── dbt/
│   ├── Dockerfile                 # dbt-core + dbt-clickhouse + dbt deps (baked in)
│   ├── dbt_project.yml            # Конфигурация слоёв и материализаций
│   ├── packages.yml               # dbt_utils 1.3, dbt_date 0.10
│   ├── profiles.yml               # clickhouse_profile → dev
│   ├── models/
│   │   ├── sources.yml            # raw_fintech: 7 источников
│   │   ├── staging/               # 7 моделей (stg_*)
│   │   ├── intermediate/          # 3 модели (int_*)
│   │   ├── dimensions/            # 5 моделей (dim_*)
│   │   ├── facts/                 # 3 модели (fct_*)
│   │   └── marts/                 # 8 моделей (mart_*)
│   ├── macros/                    # generate_surrogate_key, cents_to_dollars, date_spine, …
│   ├── seeds/                     # seed_dim_date.csv, currency_codes.csv
│   └── tests/generic/             # positive_amount (custom generic test)
├── airflow/
│   └── dags/dbt_dag.py            # DAG: pre-checks → seed → layer-by-layer run → test
└── superset/
    ├── Dockerfile
    ├── superset_config.py
    ├── superset_init.sh            # API-bootstrap: database, datasets, 8 charts, dashboard
    └── docker-entrypoint.sh
```

---

## 🔄 Kafka Ingestion Pattern

```
Postgres WAL ──► Debezium ──► Kafka Topic ──► Kafka Engine Table ──► Materialized View ──► MergeTree Table
  (изменения)    (CDC)        (JSON)           (виртуальный consumer)  (парсинг + INSERT)    (landing)
```

Debezium подписывается на **7 таблиц** Postgres через логическую репликацию (`pgoutput`). Каждое изменение (INSERT/UPDATE/DELETE) публикуется в топик `fintech.public.<table>` в формате Debezium JSON Envelope (`$.payload.after`).

В ClickHouse для каждой таблицы создаётся тройка:
- **`raw_<table>`** — Kafka Engine table (виртуальный consumer, данные исчезают после прочтения)
- **`mv_<table>`** — Materialized View (парсит JSON, приводит типы, пишет в landing)
- **`<table>`** — MergeTree landing table (финальное хранилище для dbt)

Особенности парсинга Debezium JSON в ClickHouse:
- **Boolean** (`is_active`, `is_online`): `toUInt8(lowerUTF8(JSON_VALUE(…)) = 'true')`
- **Timestamp** (epoch μs): `toDateTime64(toInt64(JSON_VALUE(…)) / 1000000, 6)`
- **Date** (days since epoch): `addDays(toDate('1970-01-01'), toInt32(JSON_VALUE(…)))`

---

## ER: Postgres OLTP-домен

```mermaid
erDiagram
  CUSTOMERS ||--o{ ACCOUNTS : owns
  CUSTOMERS ||--o{ TRANSACTIONS : initiates
  CUSTOMERS ||--o{ LOANS : borrows
  ACCOUNTS ||--o{ TRANSACTIONS : books
  ACCOUNTS ||--o{ LOANS : secures
  MERCHANTS ||--o{ TRANSACTIONS : receives
  LOANS ||--o{ LOAN_PAYMENTS : repaid_by
```

| Таблица | Строк (тест) | Описание |
|---------|-------------|----------|
| `customers` | 200 | Клиенты: ФИО, email, страна, KYC-статус, уровень риска |
| `accounts` | 400 | Счета: тип (CHECKING/SAVINGS/CREDIT), валюта, баланс, лимит |
| `merchants` | 120 | Мерчанты: категория, MCC, страна, online/offline |
| `transactions` | 50 000 | Операции: тип, канал, устройство, валюта, сумма, комиссия |
| `loans` | 150 | Кредиты: тип, ставка, срок, остаток, статус |
| `loan_payments` | 500 | Платежи по кредитам: сумма основного долга, проценты |
| `exchange_rates` | ~30 | Курсы валют: пара, дата, rate |

Таблица `exchange_rates` — справочник курсов по дате и паре валют, без FK к остальным таблицам.

---

## Star Schema

Основная звезда строится вокруг **`fct_transaction`**: одна строка — одна операция; ключевые измерения — клиент, счёт, мерчант, дата.

```mermaid
flowchart TB
  DC["<b>dim_customer</b><br>view<br>─────────────────<br>customer_sk · String SK<br>customer_id · UInt64 NK<br>full_name · String<br>email · String<br>country / city · String<br>kyc_status · LC<br>risk_level · LC<br>is_active · UInt8<br>created_at · DateTime64"]
  DA["<b>dim_account</b><br>view<br>─────────────────<br>account_sk · String SK<br>account_id · UInt64 NK<br>customer_id · UInt64 FK<br>account_type · LC<br>account_status · LC<br>currency_code · LC<br>opened_at · DateTime64"]
  DM["<b>dim_merchant</b><br>view<br>─────────────────<br>merchant_sk · String SK<br>merchant_id · UInt64 NK<br>merchant_name · String<br>merchant_category · LC<br>mcc_code · String<br>merchant_country / city · String<br>is_online · UInt8"]
  DD["<b>dim_date</b><br>view (seed)<br>─────────────────<br>date_sk · String SK<br>date_day · Date<br>year_num / quarter_num / month_num<br>day_of_month · UInt8<br>is_weekend · UInt8"]
  FT["<b>fct_transaction</b><br>table<br>─────────────────<br>transaction_sk · String SK<br>transaction_id · UInt64 NK<br>customer_id · UInt64 NK<br>customer_sk / account_sk / merchant_sk / date_sk<br>transaction_type · LC<br>transaction_status · LC<br>payment_channel · LC<br>device_type · LC<br>amount · Float64<br>amount_usd · Float64<br>fee_amount · Float64<br>is_international · UInt8<br>created_at · DateTime64"]

  DC -->|"customer_sk"| FT
  DA -->|"account_sk"| FT
  DM -->|"merchant_sk"| FT
  DD -->|"date_sk"| FT
```

*SK = surrogate key (`dbt_utils.generate_surrogate_key`), NK = natural key из источника, LC = LowCardinality.*

Дополнительные факты:
- **`fct_daily_balance`** — grain «день × счёт × клиент», снимок баланса
- **`fct_loan`** — grain «кредит», атрибуты и агрегаты платежей

---

## 🏗️ Слои dbt

```mermaid
flowchart TB
  Src["<b>sources</b><br>raw_fintech (7 таблиц)<br>MergeTree landing в ClickHouse"]
  Stg["<b>staging</b> (7 views)<br>stg_customers · stg_accounts · stg_merchants<br>stg_transactions · stg_loans · stg_loan_payments<br>stg_exchange_rates"]
  Int["<b>intermediate</b> (3 views)<br>int_transactions_enriched<br>int_customer_account_summary<br>int_loan_performance"]
  Dim["<b>dimensions</b> (5 views)<br>dim_customer · dim_account · dim_merchant<br>dim_date · dim_currency"]
  Fct["<b>facts</b> (3 tables)<br>fct_transaction · fct_daily_balance · fct_loan"]
  Mart["<b>marts</b> (8 tables)<br>daily_revenue · CLV · RFM · cohort_retention<br>payment_channel · loan_portfolio · merchant_spend<br>fraud_risk"]
  Seed["<b>seeds</b><br>seed_dim_date.csv<br>currency_codes.csv"]

  Src --> Stg --> Int --> Dim
  Int --> Fct
  Dim --> Fct
  Seed --> Dim
  Fct --> Mart
```

### Staging — очистка и CDC-дедупликация

Каждая staging-модель выполняет `row_number() over (partition by id order by updated_at desc)` для дедупликации CDC-событий: в ClickHouse MergeTree каждый апдейт из Debezium добавляет строку, staging берёт только последнюю версию.

| Модель | Источник | Что делает |
|--------|----------|-----------|
| `stg_customers` | `customers` | Переименование `id` → `customer_id`, дедупликация по `updated_at` |
| `stg_accounts` | `accounts` | `id` → `account_id`, последняя версия счёта |
| `stg_merchants` | `merchants` | `id` → `merchant_id`, дедупликация по `created_at` |
| `stg_transactions` | `transactions` | `id` → `transaction_id`, последняя версия операции |
| `stg_loans` | `loans` | `id` → `loan_id` |
| `stg_loan_payments` | `loan_payments` | `id` → `loan_payment_id` |
| `stg_exchange_rates` | `exchange_rates` | `id` → `exchange_rate_id` |

### Intermediate — обогащение и бизнес-логика

| Модель | Что делает |
|--------|-----------|
| `int_transactions_enriched` | JOIN транзакции + счёт + клиент + мерчант: добавляет `account_type`, `customer_country`, `customer_risk_level`, `merchant_category` |
| `int_customer_account_summary` | Агрегат по клиенту: `account_count`, `total_balance`, `avg_interest_rate` |
| `int_loan_performance` | Агрегат по кредиту: `payment_count`, `total_paid`, `late_payment_count` |

### Dimensions — измерения star schema

| Модель | Источник | Материализация |
|--------|----------|---------------|
| `dim_customer` | `stg_customers` | view + surrogate key |
| `dim_account` | `stg_accounts` | view + surrogate key |
| `dim_merchant` | `stg_merchants` | view + surrogate key |
| `dim_date` | seed `seed_dim_date` | view + surrogate key |
| `dim_currency` | seed `currency_codes` | view + surrogate key |

### Facts — факт-таблицы

| Модель | Grain | Источники |
|--------|-------|----------|
| `fct_transaction` | 1 транзакция | `int_transactions_enriched` + 4 dimension join |
| `fct_daily_balance` | день × счёт | `stg_accounts` |
| `fct_loan` | 1 кредит | `int_loan_performance` + surrogate key |

### Marts — бизнес-витрины

| Витрина | Метрика | Описание |
|---------|---------|----------|
| `mart_daily_revenue` | `daily_revenue_usd`, `tx_count`, `avg_ticket_usd` | Дневная выручка по успешным транзакциям |
| `mart_customer_lifetime_value` | `lifetime_value_usd`, `transaction_count` | CLV клиента (все успешные операции) |
| `mart_customer_rfm_segmentation` | `recency`, `frequency`, `monetary` → `segment` | RFM-сегментация: VIP / LOYAL / REGULAR |
| `mart_monthly_cohort_retention` | `cohort_month`, `active_customers`, `cohort_age_month` | Когортная retention: первый месяц vs активность |
| `mart_payment_channel_mix` | `amount_usd` по `payment_channel` | Объём и структура платежей по каналу |
| `mart_loan_portfolio_health` | `loan_count`, `outstanding_balance`, `risky_outstanding` | Здоровье кредитного портфеля по статусам |
| `mart_merchant_category_spend` | `total_spend_usd` по `merchant_category` | Траты по категориям мерчантов |
| `mart_fraud_risk_indicators` | `tx_count`, `intl_tx_count`, `failed_tx_count` | Клиенты с аномальной активностью (velocity, international, failed) |

---

## 🔗 Оркестрация (Airflow)

DAG **`fintech_dbt_layered_hourly`** запускается каждый час и последовательно выполняет трансформации через `DockerOperator` (каждый шаг — отдельный контейнер dbt):

```
check_clickhouse → check_data_freshness → prepare (seed) → transform (5 шагов) → dbt_test
```

![Airflow DAG](docs/dag.png)

| Группа | Задача | Команда dbt |
|--------|--------|------------|
| pre-checks | `check_clickhouse` | HTTP ping ClickHouse |
| pre-checks | `check_data_freshness` | Проверка свежести данных |
| prepare | `dbt_seed` | `seed --full-refresh` |
| transform | `run_staging` | `run --select tag:staging` |
| transform | `run_intermediate` | `run --select tag:intermediate` |
| transform | `run_dimensions` | `run --select tag:dimensions` |
| transform | `run_facts` | `run --select tag:facts` |
| transform | `run_marts` | `run --select tag:marts` |
| quality | `dbt_test` | `test` |

---

## dbt Lineage

![dbt Lineage](docs/lineage.png)

---

## 📊 Superset Dashboard

При старте Superset автоматически выполняется bootstrap-скрипт (`superset_init.sh`), который через API:
1. Создаёт подключение **ClickHouse Fintech** (`clickhousedb://admin:admin123@clickhouse:8123/default`)
2. Создаёт dataset для каждой из 8 витрин
3. Создаёт 8 чартов и привязывает к дашборду **«Fintech Analytics»**

| № | Название чарта | Тип | Витрина | Что показывает |
|---|----------------|-----|---------|----------------|
| 1 | Дневная выручка (USD) | Line (ECharts) | `mart_daily_revenue` | Динамика `daily_revenue_usd` по дням |
| 2 | RFM: клиенты по сегменту | Bar | `mart_customer_rfm_segmentation` | Число клиентов по VIP / LOYAL / REGULAR |
| 3 | Объём по каналу платежа | Bar | `mart_payment_channel_mix` | Сумма `amount_usd` по `payment_channel` |
| 4 | Кредитный портфель по статусу | Table | `mart_loan_portfolio_health` | Количество, остатки и рисковый outstanding |
| 5 | Траты по категории мерчанта | Bar | `mart_merchant_category_spend` | `total_spend_usd` по `merchant_category` |
| 6 | Когорты: активные клиенты | Table | `mart_monthly_cohort_retention` | Cohort vs activity month |
| 7 | Индикаторы риска | Table | `mart_fraud_risk_indicators` | Клиент × день: velocity, intl, failed |
| 8 | Топ клиентов по CLV | Table | `mart_customer_lifetime_value` | `lifetime_value_usd`, `transaction_count` |

![Superset Dashboard](docs/superset.png)

---

## 🧪 Полезные команды dbt

```bash
# Интерактивная оболочка в контейнере dbt
docker compose run --rm --entrypoint bash dbt

# Внутри контейнера:
dbt deps                          # установить пакеты
dbt run                           # запустить все модели
dbt run --select tag:staging      # запустить только staging
dbt run --select fct_transaction+ # модель + все downstream
dbt test                          # запустить все тесты
dbt test --select stg_customers   # тесты одной модели
dbt compile --select dim_customer # скомпилировать SQL
dbt docs generate && dbt docs serve --port 8082  # документация + lineage
dbt ls --resource-type model      # список моделей
dbt ls --resource-type test       # список тестов
```

Скомпилированный SQL (после `dbt compile`): `target/compiled/fintech_project/models/<layer>/<model>.sql`

---

## ✅ Чеклист реализации

- [x] 7 таблиц в Postgres с реалистичным генератором данных
- [x] CDC ingestion через Debezium (pgoutput, 7 таблиц)
- [x] Kafka Engine + Materialized Views для парсинга Debezium JSON
- [x] CDC-дедупликация в staging (window function + row_number)
- [x] Star schema: 5 dimensions + 3 facts с surrogate keys
- [x] 8 бизнес-витрин с финтех-метриками
- [x] 37 dbt-тестов: not_null, unique, accepted_values, relationships, custom
- [x] Airflow DAG с последовательным запуском слоёв и quality gate
- [x] Superset: автоматический bootstrap дашборда с 8 чартами
- [x] Docker Compose: 12 сервисов, healthchecks, зависимости
- [x] on-run-start cleanup для ClickHouse `__dbt_backup` таблиц
