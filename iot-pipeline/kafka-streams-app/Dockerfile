FROM gradle:8.7-jdk17 AS build
WORKDIR /workspace
COPY settings.gradle.kts build.gradle.kts gradlew gradlew.bat ./
COPY gradle ./gradle
COPY iot-pipeline/kafka-streams-app ./iot-pipeline/kafka-streams-app
RUN ./gradlew :iot-pipeline:kafka-streams-app:bootJar --no-daemon -x test

FROM eclipse-temurin:17-jre-alpine
WORKDIR /app
COPY --from=build /workspace/iot-pipeline/kafka-streams-app/build/libs/kafka-streams-app-1.0.0.jar app.jar
EXPOSE 8086
ENV SERVER_PORT=8086
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
