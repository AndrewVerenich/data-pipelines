#!/bin/bash

echo "⏳ Ждём запуск HDFS (таймаут 20 секунд)..."
sleep 20

echo "📌 Проверка конфигурации:"
hdfs getconf -confKey fs.defaultFS

echo "📤 Загружаем logs.txt в HDFS..."
hdfs dfs -mkdir -p /logs
hdfs dfs -put -f /data/logs.txt /logs/
hdfs dfs -ls /logs
