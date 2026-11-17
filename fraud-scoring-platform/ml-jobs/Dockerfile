FROM eclipse-temurin:17-jre-jammy

WORKDIR /app

# CatBoost training CLI (JVM package covers inference only)
RUN apt-get update && apt-get install -y --no-install-recommends curl ca-certificates \
  && curl -fsSL -o /usr/local/bin/catboost \
    https://github.com/catboost/catboost/releases/download/v1.2.5/catboost-linux-x86_64-1.2.5 \
  && chmod +x /usr/local/bin/catboost \
  && rm -rf /var/lib/apt/lists/*

COPY fraud-scoring-platform/ml-jobs/build/libs/fraud-ml-jobs-1.0.0.jar app.jar

ENV CATBOOST_BIN=/usr/local/bin/catboost
ENV MODEL_ROOT=/models/fraud-models

ENTRYPOINT ["java", "-jar", "app.jar"]
CMD ["train"]
