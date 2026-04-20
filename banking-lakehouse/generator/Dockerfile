FROM eclipse-temurin:17-jre-alpine

WORKDIR /app

COPY banking-lakehouse/generator/build/libs/banking-generator-1.0.0.jar app.jar

ENTRYPOINT ["java", "-jar", "app.jar"]
