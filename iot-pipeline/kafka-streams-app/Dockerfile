FROM eclipse-temurin:17-jre-alpine
RUN apk add --no-cache libstdc++
WORKDIR /app
COPY iot-pipeline/kafka-streams-app/build/libs/kafka-streams-app-1.0.0.jar app.jar
EXPOSE 8086
ENV SERVER_PORT=8086
ENTRYPOINT ["java", "-jar", "app.jar"]
