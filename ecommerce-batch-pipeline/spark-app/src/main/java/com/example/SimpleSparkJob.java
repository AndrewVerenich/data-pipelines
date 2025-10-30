package com.example;

import org.apache.spark.api.java.function.FilterFunction;
import org.apache.spark.sql.Dataset;
import org.apache.spark.sql.SparkSession;

public class SimpleSparkJob {

  public static void main(String[] args) throws InterruptedException {
    Thread.sleep(10_000);
    SparkSession spark = SparkSession.builder()
        .appName("Log Analyzer")
        .master("spark://spark-master:7077")
        .config("spark.hadoop.fs.defaultFS", "hdfs://namenode:8020")
        .getOrCreate();

    Dataset<String> logs = spark.read().textFile("hdfs://namenode:8020/logs/logs.txt");

    long totalLines = logs.count();
    long errorLines = logs.filter((FilterFunction<String>) line -> line.contains("ERROR")).count();

    System.out.println("📄 Total log lines: " + totalLines);
    System.out.println("❌ Error log lines: " + errorLines);

    spark.stop();
  }
}
