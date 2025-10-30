rootProject.name = "data-pipelines"

include(
  "iot-pipeline:iot-data-generator",
  "iot-pipeline:kafka-streams-app",
  "ecommerce-batch-pipeline:spark-app"
)
