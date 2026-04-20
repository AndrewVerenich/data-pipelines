package com.example.banking.spark;

import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.FileSystem;
import org.apache.hadoop.fs.Path;
import org.apache.spark.sql.Column;
import org.apache.spark.sql.CreateTableWriter;
import org.apache.spark.sql.Dataset;
import org.apache.spark.sql.Row;
import org.apache.spark.sql.SparkSession;
import org.apache.spark.sql.expressions.Window;
import org.apache.spark.sql.expressions.WindowSpec;
import org.apache.spark.sql.types.ArrayType;
import org.apache.spark.sql.types.StructField;

import scala.collection.JavaConverters;
import scala.collection.Seq;

import java.net.URI;
import java.util.ArrayList;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

import static org.apache.spark.sql.functions.col;
import static org.apache.spark.sql.functions.days;
import static org.apache.spark.sql.functions.make_date;
import static org.apache.spark.sql.functions.row_number;
import static org.apache.spark.sql.functions.to_date;
import static org.apache.spark.sql.functions.to_timestamp;

public final class BronzeToSilverJob {

    private BronzeToSilverJob() {
    }

    public static void main(String[] args) throws Exception {
        Map<String, String> parsed = parseArgs(args);
        String table = parsed.get("table");
        int waitSeconds = Integer.parseInt(parsed.getOrDefault("wait-seconds", "300"));
        int waitIntervalSeconds = Integer.parseInt(parsed.getOrDefault("wait-interval-seconds", "10"));

        if (!"customers".equals(table) && !"accounts".equals(table) && !"transactions".equals(table)) {
            throw new IllegalArgumentException("Unsupported table: " + table);
        }

        SparkSession spark = SparkSession.builder().appName("banking-bronze-to-silver-java").getOrCreate();
        try {
            spark.sql("CREATE NAMESPACE IF NOT EXISTS iceberg.silver");
            spark.sql("CREATE NAMESPACE IF NOT EXISTS iceberg.gold");

            String sourcePath = "s3a://lakehouse/bronze/banking." + table + "/";
            waitForPath(spark, sourcePath, waitSeconds, waitIntervalSeconds);

            Dataset<Row> bronzeDf = spark.read().option("recursiveFileLookup", "true").json(sourcePath);
            Dataset<Row> silverDf = transform(table, bronzeDf);

            String targetTable = "iceberg.silver." + table;
            CreateTableWriter<Row> writer = silverDf.writeTo(targetTable).using("iceberg");
            if ("transactions".equals(table)) {
                Seq<Column> noExtraTransforms =
                        JavaConverters.asScalaBufferConverter(new ArrayList<Column>()).asScala().toSeq();
                writer = writer.partitionedBy(days(col("timestamp")), noExtraTransforms);
            }
            writer.createOrReplace();
        } finally {
            spark.stop();
        }
    }

    private static void waitForPath(SparkSession spark, String sourcePath, int waitSeconds, int waitIntervalSeconds) throws Exception {
        Configuration conf = spark.sparkContext().hadoopConfiguration();
        FileSystem fileSystem = FileSystem.get(URI.create(sourcePath), conf);
        Path path = new Path(sourcePath);

        Instant deadline = Instant.now().plus(Duration.ofSeconds(waitSeconds));
        while (Instant.now().isBefore(deadline)) {
            if (fileSystem.exists(path)) {
                return;
            }
            Thread.sleep(waitIntervalSeconds * 1000L);
        }

        throw new IllegalStateException("Path did not appear in time: " + sourcePath);
    }

    private static Dataset<Row> normalizeLocalDate(Dataset<Row> df, String columnName) {
        StructField field = df.schema().apply(columnName);
        if (field.dataType() instanceof ArrayType) {
            return df.withColumn(
                    columnName,
                    make_date(
                            col(columnName).getItem(0).cast("int"),
                            col(columnName).getItem(1).cast("int"),
                            col(columnName).getItem(2).cast("int")
                    )
            );
        }
        return df.withColumn(columnName, to_date(col(columnName)));
    }

    private static Dataset<Row> transform(String table, Dataset<Row> df) {
        Dataset<Row> transformed;
        String dedupKey;
        String[] requiredCols;

        switch (table) {
            case "customers":
                transformed = df
                        .withColumn("customer_id", col("customer_id").cast("bigint"))
                        .withColumn("age", col("age").cast("int"));
                transformed = normalizeLocalDate(transformed, "registration_date")
                        .withColumn("timestamp", to_timestamp(col("timestamp")));
                dedupKey = "customer_id";
                requiredCols = new String[]{"customer_id", "email", "timestamp"};
                break;
            case "accounts":
                transformed = df
                        .withColumn("account_id", col("account_id").cast("bigint"))
                        .withColumn("customer_id", col("customer_id").cast("bigint"));
                transformed = normalizeLocalDate(transformed, "opened_at")
                        .withColumn("timestamp", to_timestamp(col("timestamp")));
                dedupKey = "account_id";
                requiredCols = new String[]{"account_id", "customer_id", "account_type", "timestamp"};
                break;
            case "transactions":
                transformed = df
                        .withColumn("account_id", col("account_id").cast("bigint"))
                        .withColumn("amount", col("amount").cast("decimal(18,2)"))
                        .withColumn("timestamp", to_timestamp(col("timestamp")));
                dedupKey = "transaction_id";
                requiredCols = new String[]{"transaction_id", "account_id", "amount", "transaction_type", "timestamp"};
                break;
            default:
                throw new IllegalArgumentException("Unsupported table: " + table);
        }

        Dataset<Row> cleaned = transformed.na().drop(requiredCols);
        WindowSpec windowSpec = Window.partitionBy(dedupKey).orderBy(col("timestamp").desc());
        return cleaned
                .withColumn("_rn", row_number().over(windowSpec))
                .filter(col("_rn").equalTo(1))
                .drop("_rn");
    }

    private static Map<String, String> parseArgs(String[] args) {
        Map<String, String> parsed = new HashMap<>();
        for (int i = 0; i < args.length; i++) {
            String key = args[i];
            if (!key.startsWith("--")) {
                continue;
            }
            if (i + 1 >= args.length) {
                throw new IllegalArgumentException("Missing value for argument: " + key);
            }
            parsed.put(key.substring(2), args[++i]);
        }
        if (!parsed.containsKey("table")) {
            throw new IllegalArgumentException("Missing required argument: --table");
        }
        return parsed;
    }
}
