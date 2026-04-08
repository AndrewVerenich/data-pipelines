# Build from monorepo root: docker build -f iot-pipeline/smart-home-simulator/Dockerfile ..
FROM gradle:8.7-jdk17 AS build
WORKDIR /workspace
COPY settings.gradle.kts build.gradle.kts gradlew gradlew.bat ./
COPY gradle ./gradle
COPY iot-pipeline/smart-home-simulator ./iot-pipeline/smart-home-simulator
RUN ./gradlew :iot-pipeline:smart-home-simulator:bootJar --no-daemon -x test

FROM eclipse-temurin:17-jre-alpine
WORKDIR /app
COPY --from=build /workspace/iot-pipeline/smart-home-simulator/build/libs/smart-home-simulator-1.0.0.jar app.jar
EXPOSE 8085
ENV SERVER_PORT=8085
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
