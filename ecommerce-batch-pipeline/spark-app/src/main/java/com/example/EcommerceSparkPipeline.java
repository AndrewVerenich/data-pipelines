package com.example;

import static org.apache.spark.sql.functions.broadcast;
import static org.apache.spark.sql.functions.col;
import static org.apache.spark.sql.functions.date_format;
import static org.apache.spark.sql.functions.lit;
import static org.apache.spark.sql.functions.row_number;
import static org.apache.spark.sql.functions.to_date;
import static org.apache.spark.sql.functions.to_timestamp;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import org.apache.spark.sql.Dataset;
import org.apache.spark.sql.Row;
import org.apache.spark.sql.SaveMode;
import org.apache.spark.sql.SparkSession;
import org.apache.spark.sql.expressions.Window;
import org.apache.spark.sql.expressions.WindowSpec;
import org.apache.spark.sql.types.DataTypes;

/**
 * Batch pipeline: HDFS bronze JSON → silver (dedupe, joins) → ClickHouse raw tables.
 * Steps: bronze | silver | load_ch | all (default all).
 */
public final class EcommerceSparkPipeline {

  private static final String HDFS = "hdfs://namenode:8020";
  private static final String CH_URL = "jdbc:clickhouse://clickhouse:8123/ecommerce_dwh";
  private static final String CH_USER = "admin";
  private static final String CH_PASSWORD = "admin123";

  public static void main(String[] args) throws Exception {
    Thread.sleep(5_000);
    Map<String, String> opts = parseArgs(args);
    String batchId = opts.getOrDefault("batch-id", "batch_default").replace("'", "");
    String step = opts.getOrDefault("step", "all").toLowerCase(Locale.ROOT);

    SparkSession spark =
        SparkSession.builder()
            .appName("EcommerceSparkPipeline-" + step + "-" + batchId)
            .master("spark://spark-master:7077")
            .config("spark.hadoop.fs.defaultFS", HDFS)
            .config("spark.sql.shuffle.partitions", "8")
            .getOrCreate();

    try {
      switch (step) {
        case "all":
          runBronze(spark, batchId);
          runSilver(spark, batchId);
          runLoadCh(spark, batchId);
          break;
        case "bronze":
          runBronze(spark, batchId);
          break;
        case "silver":
          runSilver(spark, batchId);
          break;
        case "load_ch":
          runLoadCh(spark, batchId);
          break;
        default:
          throw new IllegalArgumentException("Unknown --step: " + step);
      }
    } finally {
      spark.stop();
    }
    System.out.println("EcommerceSparkPipeline FINISHED step=" + step + " batch=" + batchId);
  }

  private static Map<String, String> parseArgs(String[] args) {
    Map<String, String> m = new HashMap<>();
    for (int i = 0; i < args.length; i++) {
      String a = args[i];
      if (a.startsWith("--")) {
        String key = a.substring(2);
        if (i + 1 < args.length && !args[i + 1].startsWith("--")) {
          m.put(key, args[i + 1]);
          i++;
        }
      }
    }
    return m;
  }

  private static void runBronze(SparkSession spark, String batchId) {
    Dataset<Row> events =
        spark
            .read()
            .option("multiLine", false)
            .json(HDFS + "/raw/events/*.json*")
            .withColumnRenamed("userId", "user_id")
            .withColumnRenamed("sessionId", "session_id")
            .withColumnRenamed("errorType", "error_type")
            .withColumnRenamed("paymentMethod", "payment_method")
            .withColumnRenamed("productId", "product_id")
            .withColumnRenamed("orderId", "order_id");

    events =
        events
            .withColumn("event_ts", to_timestamp(col("timestamp"), "yyyy-MM-dd'T'HH:mm:ss"))
            .withColumn("ingest_batch_id", lit(batchId))
            .drop("timestamp");

    String out = HDFS + "/processed/bronze/" + sanitizePath(batchId) + "/events";
    events.write().mode(SaveMode.Overwrite).parquet(out);
    System.out.println("Bronze written: " + out);
  }

  private static void runSilver(SparkSession spark, String batchId) {
    String bronzePath = HDFS + "/processed/bronze/" + sanitizePath(batchId) + "/events";
    Dataset<Row> events = spark.read().parquet(bronzePath);

    Dataset<Row> users =
        spark
            .read()
            .json(HDFS + "/raw/reference/users.json*")
            .withColumnRenamed("userId", "user_id")
            .select("user_id", "email", "country", "cohort");

    Dataset<Row> products =
        spark
            .read()
            .json(HDFS + "/raw/reference/products.json*")
            .withColumnRenamed("productId", "product_id")
            .select(
                col("product_id"),
                col("productName").alias("product_name"),
                col("category").alias("product_category_ref"),
                col("unitPrice").cast(DataTypes.DoubleType).alias("unit_price"));

    Dataset<Row> ev = events.withColumnRenamed("category", "event_category");

    Dataset<Row> enriched =
        ev.join(broadcast(users), ev.col("user_id").equalTo(users.col("user_id")), "left")
            .drop(users.col("user_id"))
            .withColumnRenamed("email", "user_email")
            .withColumnRenamed("country", "user_country")
            .withColumnRenamed("cohort", "user_cohort");

    enriched =
        enriched.join(
                broadcast(products),
                enriched.col("product_id").equalTo(products.col("product_id")),
                "left")
            .drop(products.col("product_id"));

    enriched =
        enriched
            .withColumn("category", col("event_category"))
            .drop("event_category")
            .withColumn("event_date", to_date(col("event_ts")))
            .withColumn("minute", date_format(col("event_ts"), "yyyy-MM-dd HH:mm"));

    WindowSpec w =
        Window.partitionBy("user_id", "session_id", "event", "event_ts").orderBy(col("level"));
    enriched =
        enriched.withColumn("_rn", row_number().over(w)).filter(col("_rn").equalTo(1)).drop("_rn");

    String out = HDFS + "/processed/silver/" + sanitizePath(batchId) + "/events";
    enriched.write().mode(SaveMode.Overwrite).parquet(out);
    System.out.println("Silver written: " + out);

    Dataset<Row> usersForCh =
        users
            .withColumn("ingest_batch_id", lit(batchId))
            .select("ingest_batch_id", "user_id", "email", "country", "cohort");

    Dataset<Row> productsForCh =
        products
            .withColumn("ingest_batch_id", lit(batchId))
            .select(
                col("ingest_batch_id"),
                col("product_id"),
                col("product_name"),
                col("product_category_ref").alias("category"),
                col("unit_price"));

    usersForCh.write().mode(SaveMode.Overwrite).parquet(HDFS + "/processed/silver/" + sanitizePath(batchId) + "/ref_users");
    productsForCh
        .write()
        .mode(SaveMode.Overwrite)
        .parquet(HDFS + "/processed/silver/" + sanitizePath(batchId) + "/ref_products");
  }

  private static void runLoadCh(SparkSession spark, String batchId) throws Exception {
    String silverPath = HDFS + "/processed/silver/" + sanitizePath(batchId) + "/events";
    Dataset<Row> silver = spark.read().parquet(silverPath);

    Dataset<Row> forCh =
        silver.select(
            col("ingest_batch_id"),
            col("event_date"),
            col("event_ts"),
            col("minute"),
            col("level"),
            col("event"),
            col("user_id"),
            col("session_id"),
            col("device"),
            col("page"),
            col("error_type"),
            col("payment_method"),
            col("category"),
            col("product_id"),
            col("order_id"),
            col("user_country"),
            col("user_email"),
            col("product_name"),
            col("product_category_ref"),
            col("unit_price"));

    dropPartitionQuiet("raw_ecommerce_events", batchId);
    dropPartitionQuiet("raw_ref_users", batchId);
    dropPartitionQuiet("raw_ref_products", batchId);

    writeJdbc(forCh, "raw_ecommerce_events");

    Dataset<Row> refUsers = spark.read().parquet(HDFS + "/processed/silver/" + sanitizePath(batchId) + "/ref_users");
    writeJdbc(refUsers, "raw_ref_users");

    Dataset<Row> refProducts =
        spark.read().parquet(HDFS + "/processed/silver/" + sanitizePath(batchId) + "/ref_products");
    writeJdbc(refProducts, "raw_ref_products");
  }

  private static void dropPartitionQuiet(String table, String batchId) {
    String safe = batchId.replace("'", "''");
    String sql =
        String.format("ALTER TABLE ecommerce_dwh.%s DROP PARTITION ('%s')", table, safe);
    try (Connection c = DriverManager.getConnection(CH_URL, CH_USER, CH_PASSWORD);
        Statement s = c.createStatement()) {
      s.execute(sql);
      System.out.println("Dropped partition: " + table + " / " + batchId);
    } catch (Exception e) {
      System.out.println("Drop partition skipped (" + table + "): " + e.getMessage());
    }
  }

  private static void writeJdbc(Dataset<Row> df, String table) {
    df.write()
        .mode(SaveMode.Append)
        .format("jdbc")
        .option("url", CH_URL)
        .option("dbtable", "ecommerce_dwh." + table)
        .option("user", CH_USER)
        .option("password", CH_PASSWORD)
        .option("driver", "com.clickhouse.jdbc.ClickHouseDriver")
        .option("batchsize", "5000")
        .save();
  }

  private static String sanitizePath(String batchId) {
    return batchId.replaceAll("[^a-zA-Z0-9_.-]", "_");
  }
}
