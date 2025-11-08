#!/bin/bash
set -e

# Запускаем Superset сервер в фоне
superset run -p 8088 -h 0.0.0.0 &
SUPERSET_PID=$!

# Ждём пока сервер поднимется
sleep 20

# Запускаем инициализацию
/superset/superset_init.sh

# Держим основной процесс
wait $SUPERSET_PID
