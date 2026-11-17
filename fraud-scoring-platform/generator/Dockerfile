FROM eclipse-temurin:17-jre-alpine
WORKDIR /app
COPY fraud-scoring-platform/generator/build/libs/fraud-generator-1.0.0.jar app.jar
ENTRYPOINT ["java", "-jar", "app.jar"]
