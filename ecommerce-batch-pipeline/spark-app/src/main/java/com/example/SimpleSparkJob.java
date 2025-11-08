package com.example;

import static org.apache.spark.sql.functions.col;
import static org.apache.spark.sql.functions.collect_list;
import static org.apache.spark.sql.functions.date_format;
import static org.apache.spark.sql.functions.desc;
import static org.apache.spark.sql.functions.to_timestamp;

import org.apache.spark.sql.Dataset;
import org.apache.spark.sql.Row;
import org.apache.spark.sql.SparkSession;

public class SimpleSparkJob {

  public static void main(String[] args) throws InterruptedException {
    Thread.sleep(10_000);
    SparkSession spark = SparkSession.builder()
        .appName("Ecommerce Log Analyzer")
        .master("spark://spark-master:7077")
        .config("spark.hadoop.fs.defaultFS", "hdfs://namenode:8020")
        .getOrCreate();

    Dataset<Row> logs = spark.read().json("hdfs://namenode:8020/logs/logs.txt");

    logs = logs.withColumn("minute", date_format(col("timestamp"), "yyyy-MM-dd HH:mm"));
    logs = logs.withColumn(
        "timestamp",
        to_timestamp(col("timestamp"), "yyyy-MM-dd'T'HH:mm:ss")
    );

    logs.groupBy("event").count().orderBy(desc("count")).show();
    logs.filter(col("level").equalTo("ERROR")).groupBy("errorType").count().show();
    logs.groupBy("minute").count().orderBy("minute").show();
    logs.groupBy("userId").agg(collect_list("event").alias("events")).show(false);
    logs.filter(col("event").equalTo("Product viewed")).groupBy("category").count().show();
    logs.groupBy("device").count().show();

    logs.write()
        .mode("append")
        .format("jdbc")
        .option("url", "jdbc:postgresql://postgres:5432/ecommerce_logs")
        .option("dbtable", "log_events")
        .option("user", "spark")
        .option("password", "sparkpass")
        .option("driver", "org.postgresql.Driver")
        .save();
    System.out.println("FINISHED");

    spark.stop();
  }
}
