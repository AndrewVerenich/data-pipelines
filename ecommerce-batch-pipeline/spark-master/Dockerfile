FROM apache/spark:3.5.0

USER root
RUN mkdir -p /opt/spark-app
COPY spark-submit.sh /opt/spark-app/spark-submit.sh
RUN chmod +x /opt/spark-app/spark-submit.sh
