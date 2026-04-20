package com.example.banking.spark;

import org.apache.spark.sql.Dataset;
import org.apache.spark.sql.Row;
import org.apache.spark.sql.SparkSession;

import java.util.HashMap;
import java.util.Map;

public final class SilverToGoldJob {

    private SilverToGoldJob() {
    }

    public static void main(String[] args) {
        Map<String, String> parsed = parseArgs(args);
        String mart = parsed.get("mart");

        if (!isSupportedMart(mart)) {
            throw new IllegalArgumentException("Unsupported mart: " + mart);
        }

        SparkSession spark = SparkSession.builder().appName("banking-silver-to-gold-java").getOrCreate();
        try {
            spark.sql("CREATE NAMESPACE IF NOT EXISTS iceberg.gold");
            Dataset<Row> martDf = spark.sql(buildQuery(mart));
            martDf.writeTo("iceberg.gold." + mart).using("iceberg").createOrReplace();
        } finally {
            spark.stop();
        }
    }

    private static boolean isSupportedMart(String mart) {
        return "spending_by_category".equals(mart)
                || "customer_segments".equals(mart)
                || "anomaly_flags".equals(mart)
                || "monthly_cashflow".equals(mart)
                || "channel_analysis".equals(mart);
    }

    private static String buildQuery(String mart) {
        if ("spending_by_category".equals(mart)) {
            return "SELECT "
                    + "t.account_id, "
                    + "a.customer_id, "
                    + "t.category, "
                    + "date_trunc('month', t.timestamp) AS month, "
                    + "SUM(t.amount) AS total_amount, "
                    + "COUNT(*) AS tx_count, "
                    + "AVG(t.amount) AS avg_amount "
                    + "FROM iceberg.silver.transactions t "
                    + "JOIN iceberg.silver.accounts a ON t.account_id = a.account_id "
                    + "WHERE t.transaction_type = 'debit' "
                    + "GROUP BY 1, 2, 3, 4";
        }

        if ("customer_segments".equals(mart)) {
            return "WITH base AS ( "
                    + "SELECT "
                    + "a.customer_id, "
                    + "datediff(current_date(), to_date(MAX(t.timestamp))) AS recency_days, "
                    + "COUNT(*) AS frequency, "
                    + "SUM(t.amount) AS monetary "
                    + "FROM iceberg.silver.transactions t "
                    + "JOIN iceberg.silver.accounts a ON t.account_id = a.account_id "
                    + "GROUP BY a.customer_id "
                    + "), scored AS ( "
                    + "SELECT "
                    + "customer_id, "
                    + "recency_days, "
                    + "frequency, "
                    + "monetary, "
                    + "(6 - ntile(5) OVER (ORDER BY recency_days ASC)) AS r_score, "
                    + "ntile(5) OVER (ORDER BY frequency ASC) AS f_score, "
                    + "ntile(5) OVER (ORDER BY monetary ASC) AS m_score "
                    + "FROM base "
                    + ") "
                    + "SELECT "
                    + "customer_id, "
                    + "recency_days, "
                    + "frequency, "
                    + "monetary, "
                    + "r_score + f_score + m_score AS rfm_score, "
                    + "CASE "
                    + "WHEN r_score + f_score + m_score >= 13 THEN 'Champions' "
                    + "WHEN r_score + f_score + m_score >= 10 THEN 'Loyal' "
                    + "WHEN r_score + f_score + m_score >= 7 THEN 'At Risk' "
                    + "WHEN r_score + f_score + m_score >= 4 THEN 'Hibernating' "
                    + "ELSE 'Lost' "
                    + "END AS segment "
                    + "FROM scored";
        }

        if ("anomaly_flags".equals(mart)) {
            return "WITH tx AS ( "
                    + "SELECT "
                    + "t.transaction_id, "
                    + "t.account_id, "
                    + "a.customer_id, "
                    + "t.amount, "
                    + "t.timestamp, "
                    + "AVG(t.amount) OVER (PARTITION BY a.customer_id) AS mean_amount, "
                    + "STDDEV(t.amount) OVER (PARTITION BY a.customer_id) AS std_amount, "
                    + "COUNT(*) OVER ( "
                    + "PARTITION BY a.customer_id, window(t.timestamp, '10 minutes') "
                    + ") AS tx_in_10m "
                    + "FROM iceberg.silver.transactions t "
                    + "JOIN iceberg.silver.accounts a ON t.account_id = a.account_id "
                    + ") "
                    + "SELECT "
                    + "transaction_id, "
                    + "account_id, "
                    + "customer_id, "
                    + "amount, "
                    + "timestamp, "
                    + "CASE "
                    + "WHEN std_amount IS NOT NULL AND amount > mean_amount + 3 * std_amount THEN 'high_amount' "
                    + "WHEN hour(timestamp) BETWEEN 0 AND 5 THEN 'night_activity' "
                    + "WHEN tx_in_10m > 5 THEN 'high_velocity' "
                    + "ELSE NULL "
                    + "END AS anomaly_reason "
                    + "FROM tx "
                    + "WHERE "
                    + "(std_amount IS NOT NULL AND amount > mean_amount + 3 * std_amount) "
                    + "OR hour(timestamp) BETWEEN 0 AND 5 "
                    + "OR tx_in_10m > 5";
        }

        if ("monthly_cashflow".equals(mart)) {
            return "SELECT "
                    + "t.account_id, "
                    + "a.customer_id, "
                    + "date_trunc('month', t.timestamp) AS month, "
                    + "SUM(CASE WHEN t.transaction_type = 'credit' THEN t.amount ELSE 0 END) AS total_credit, "
                    + "SUM(CASE WHEN t.transaction_type = 'debit' THEN t.amount ELSE 0 END) AS total_debit, "
                    + "SUM(CASE WHEN t.transaction_type = 'credit' THEN t.amount ELSE 0 END) "
                    + "- SUM(CASE WHEN t.transaction_type = 'debit' THEN t.amount ELSE 0 END) AS net_cashflow "
                    + "FROM iceberg.silver.transactions t "
                    + "JOIN iceberg.silver.accounts a ON t.account_id = a.account_id "
                    + "GROUP BY 1, 2, 3";
        }

        return "SELECT "
                + "t.channel, "
                + "date_trunc('month', t.timestamp) AS month, "
                + "COUNT(*) AS tx_count, "
                + "SUM(t.amount) AS total_amount, "
                + "COUNT(DISTINCT a.customer_id) AS unique_customers, "
                + "AVG(t.amount) AS avg_amount "
                + "FROM iceberg.silver.transactions t "
                + "JOIN iceberg.silver.accounts a ON t.account_id = a.account_id "
                + "GROUP BY 1, 2";
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
        if (!parsed.containsKey("mart")) {
            throw new IllegalArgumentException("Missing required argument: --mart");
        }
        return parsed;
    }
}
