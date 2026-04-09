FROM eclipse-temurin:17-jre-alpine
WORKDIR /app
COPY iot-pipeline/smart-home-simulator/build/libs/smart-home-simulator-1.0.0.jar app.jar
EXPOSE 8085
ENV SERVER_PORT=8085
ENTRYPOINT ["java", "-jar", "app.jar"]
