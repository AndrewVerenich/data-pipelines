import argparse

from pyspark.sql import SparkSession
from pyspark.sql.functions import col, days, make_date, row_number, to_date, to_timestamp
from pyspark.sql.window import Window
from pyspark.sql.types import ArrayType


def _parse_args():
    parser = argparse.ArgumentParser()
    parser.add_argument("--table", required=True, choices=["customers", "accounts", "transactions"])
    return parser.parse_args()


def _build_spark() -> SparkSession:
    return SparkSession.builder.appName("banking-bronze-to-silver").getOrCreate()


def _normalize_local_date(df, column_name: str):
    data_type = df.schema[column_name].dataType
    if isinstance(data_type, ArrayType):
        return df.withColumn(
            column_name,
            make_date(
                col(column_name).getItem(0).cast("int"),
                col(column_name).getItem(1).cast("int"),
                col(column_name).getItem(2).cast("int"),
            ),
        )
    return df.withColumn(column_name, to_date(col(column_name)))


def _transform(table: str, df):
    if table == "customers":
        transformed = df.withColumn("customer_id", col("customer_id").cast("bigint")).withColumn(
            "age", col("age").cast("int")
        )
        transformed = _normalize_local_date(transformed, "registration_date").withColumn(
            "timestamp", to_timestamp("timestamp")
        )
        dedup_key = "customer_id"
        required_cols = ["customer_id", "email", "timestamp"]
    elif table == "accounts":
        transformed = df.withColumn("account_id", col("account_id").cast("bigint")).withColumn(
            "customer_id", col("customer_id").cast("bigint")
        )
        transformed = _normalize_local_date(transformed, "opened_at").withColumn(
            "timestamp", to_timestamp("timestamp")
        )
        dedup_key = "account_id"
        required_cols = ["account_id", "customer_id", "account_type", "timestamp"]
    else:
        transformed = (
            df.withColumn("account_id", col("account_id").cast("bigint"))
            .withColumn("amount", col("amount").cast("decimal(18,2)"))
            .withColumn("timestamp", to_timestamp("timestamp"))
        )
        dedup_key = "transaction_id"
        required_cols = ["transaction_id", "account_id", "amount", "transaction_type", "timestamp"]

    cleaned = transformed.dropna(subset=required_cols)
    window = Window.partitionBy(dedup_key).orderBy(col("timestamp").desc())
    return cleaned.withColumn("_rn", row_number().over(window)).filter(col("_rn") == 1).drop("_rn")


def main():
    args = _parse_args()
    spark = _build_spark()

    spark.sql("CREATE NAMESPACE IF NOT EXISTS iceberg.silver")
    spark.sql("CREATE NAMESPACE IF NOT EXISTS iceberg.gold")

    source_path = f"s3a://lakehouse/bronze/banking.{args.table}/"
    bronze_df = spark.read.option("recursiveFileLookup", "true").json(source_path)
    silver_df = _transform(args.table, bronze_df)

    target_table = f"iceberg.silver.{args.table}"
    writer = silver_df.writeTo(target_table).using("iceberg")
    if args.table == "transactions":
        writer = writer.partitionedBy(days(col("timestamp")))
    writer.createOrReplace()
    spark.stop()


if __name__ == "__main__":
    main()
