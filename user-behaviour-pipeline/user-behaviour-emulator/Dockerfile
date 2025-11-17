FROM eclipse-temurin:17-jre
WORKDIR /app
COPY build/libs/user-behaviour-emulator-1.0.0.jar app.jar
EXPOSE 8081
ENTRYPOINT ["java","-jar","app.jar"]
