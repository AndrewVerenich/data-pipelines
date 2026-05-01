rootProject.name = "data-pipelines"

include(
  "iot-pipeline:smart-home-simulator",
  "iot-pipeline:kafka-streams-app",
  "ecommerce-batch-pipeline:spark-app",
  "clickstream-analytics-pipeline:gateway-websocket",
  "clickstream-analytics-pipeline:clickstream-emulator",
  "clickstream-analytics-pipeline:flink-job",
  "clickstream-analytics-pipeline:config-publisher",
  "marketing-analytics-platform:event-producer",
  "banking-lakehouse:generator",
  "banking-lakehouse:spark",
)
