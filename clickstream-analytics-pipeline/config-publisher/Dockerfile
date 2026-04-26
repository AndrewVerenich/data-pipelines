FROM eclipse-temurin:17-jre
WORKDIR /app
COPY build/libs/config-publisher.jar app.jar
ENTRYPOINT ["java","-jar","app.jar"]
