FROM maven:3.9.9-eclipse-temurin-17 AS spark-jobs-build

WORKDIR /build
COPY java/pom.xml ./pom.xml
COPY java/src ./src
RUN mvn -q -DskipTests package

FROM apache/spark:3.5.0

USER root

RUN curl -L -o /opt/spark/jars/iceberg-spark-runtime-3.5_2.12-1.5.2.jar \
    https://repo1.maven.org/maven2/org/apache/iceberg/iceberg-spark-runtime-3.5_2.12/1.5.2/iceberg-spark-runtime-3.5_2.12-1.5.2.jar && \
    curl -L -o /opt/spark/jars/iceberg-aws-bundle-1.5.2.jar \
    https://repo1.maven.org/maven2/org/apache/iceberg/iceberg-aws-bundle/1.5.2/iceberg-aws-bundle-1.5.2.jar && \
    curl -L -o /opt/spark/jars/postgresql-42.7.3.jar \
    https://repo1.maven.org/maven2/org/postgresql/postgresql/42.7.3/postgresql-42.7.3.jar && \
    curl -L -o /opt/spark/jars/hadoop-aws-3.3.4.jar \
    https://repo1.maven.org/maven2/org/apache/hadoop/hadoop-aws/3.3.4/hadoop-aws-3.3.4.jar && \
    curl -L -o /opt/spark/jars/aws-java-sdk-bundle-1.12.262.jar \
    https://repo1.maven.org/maven2/com/amazonaws/aws-java-sdk-bundle/1.12.262/aws-java-sdk-bundle-1.12.262.jar

COPY jobs/ /opt/spark-jobs/
COPY --from=spark-jobs-build /build/target/banking-spark-jobs-1.0.0.jar /opt/spark-jobs/banking-spark-jobs.jar

ENV AWS_REGION=us-east-1
ENV AWS_ACCESS_KEY_ID=admin
ENV AWS_SECRET_ACCESS_KEY=admin123
