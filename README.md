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
