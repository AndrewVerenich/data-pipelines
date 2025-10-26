CREATE TABLE sensor_aggregates_kafka
(
    device_id    String,
    window_start DateTime,
    window_end   DateTime,
    avg_temp     Float64,
    avg_humidity Float64,
    avg_pressure Float64,
    count        UInt64
) ENGINE = Kafka
SETTINGS kafka_broker_list = 'kafka:9092',
         kafka_topic_list = 'sensor.aggregates',
         kafka_group_name = 'clickhouse-consumer',
         kafka_format = 'JSONEachRow',
         kafka_num_consumers = 1;

CREATE TABLE sensor_aggregates
(
    device_id    String,
    window_start DateTime,
    window_end   DateTime,
    avg_temp     Float64,
    avg_humidity Float64,
    avg_pressure Float64,
    count        UInt64
) ENGINE = MergeTree()
ORDER BY window_start;

CREATE
MATERIALIZED VIEW sensor_aggregates_mv TO sensor_aggregates AS
SELECT *
FROM sensor_aggregates_kafka;
