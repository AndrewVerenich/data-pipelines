FROM apache/spark:3.5.0

USER root

RUN apt-get update && \
    apt-get install -y curl openjdk-11-jdk unzip && \
    curl -L https://downloads.apache.org/incubator/livy/0.8.0-incubating/apache-livy-0.8.0-incubating_2.12-bin.zip -o livy.zip && \
    unzip livy.zip -d /opt/ && \
    mv /opt/apache-livy-0.8.0-incubating_2.12-bin /opt/livy && \
    rm livy.zip && \
    mkdir -p /opt/livy/logs && \
    chmod -R 777 /opt/livy/logs

ENV LIVY_HOME=/opt/livy
ENV SPARK_HOME=/opt/spark
ENV PATH=$PATH:$LIVY_HOME/bin

EXPOSE 8998

CMD ["/opt/livy/bin/livy-server"]
