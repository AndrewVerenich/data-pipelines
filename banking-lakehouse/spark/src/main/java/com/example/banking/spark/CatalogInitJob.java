package com.example.banking.spark;

import org.apache.spark.sql.SparkSession;

public final class CatalogInitJob {

  private CatalogInitJob() {
  }

  public static void main(String[] args) {
    SparkSession spark = SparkSession.builder().appName("banking-catalog-init-java").getOrCreate();
    try {
      spark.sql("CREATE NAMESPACE IF NOT EXISTS iceberg.silver");
      spark.sql("CREATE NAMESPACE IF NOT EXISTS iceberg.gold");
    } finally {
      spark.stop();
    }
  }
}
