rootProject.name = "data-pipelines"

include(
  "iot-pipeline:iot-data-generator",
  "iot-pipeline:kafka-streams-app",
  "ecommerce-batch-pipeline:spark-app",
  "user-behaviour-pipeline:gateway-websocket",
  "user-behaviour-pipeline:user-behaviour-emulator"
)
