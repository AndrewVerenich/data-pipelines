#!/bin/bash
echo "Запуск Spark job..."
/opt/spark/bin/spark-submit \
  --class com.example.SimpleSparkJob \
  --master spark://spark-master:7077 \
  /opt/spark-jars/spark-app-1.0-all.jar

