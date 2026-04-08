rootProject.name = "data-pipelines"

include(
  "iot-pipeline:smart-home-simulator",
  "iot-pipeline:kafka-streams-app",
  "ecommerce-batch-pipeline:spark-app",
  "user-behaviour-pipeline:gateway-websocket",
  "user-behaviour-pipeline:user-behaviour-emulator",
  "user-behaviour-pipeline:flink-job",
  "marketing-analytics-platform:event-producer",
)
