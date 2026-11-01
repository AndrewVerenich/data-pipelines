package com.example.fraud.ml.clickhouse

import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import java.sql.DriverManager
import java.sql.ResultSet
import java.sql.Timestamp
import java.time.Instant

@Component
class ClickHouseClient(
  @Value("\${app.clickhouse.url}") private val url: String,
  @Value("\${app.clickhouse.user}") private val user: String,
  @Value("\${app.clickhouse.password}") private val password: String
) {

  fun <T> query(sql: String, mapper: (ResultSet) -> T): List<T> {
    DriverManager.getConnection(url, user, password).use { conn ->
      conn.createStatement().use { st ->
        st.executeQuery(sql).use { rs ->
          val out = mutableListOf<T>()
          while (rs.next()) out += mapper(rs)
          return out
        }
      }
    }
  }

  fun execute(sql: String) {
    DriverManager.getConnection(url, user, password).use { conn ->
      conn.createStatement().use { st -> st.execute(sql) }
    }
  }

  fun insertTrainMetrics(
    trainedAt: Instant,
    modelVersion: String,
    auc: Double,
    prAuc: Double,
    recallAt1pctFpr: Double,
    rowsTrain: Long,
    promoted: Int
  ) {
    execute(
      """
      CREATE TABLE IF NOT EXISTS ml_train_metrics (
          trained_at DateTime64(3, 'UTC'),
          model_version String,
          auc Float64,
          pr_auc Float64,
          recall_at_1pct_fpr Float64,
          rows_train UInt64,
          promoted UInt8
      ) ENGINE = MergeTree ORDER BY trained_at
      """.trimIndent()
    )
    DriverManager.getConnection(url, user, password).use { conn ->
      conn.prepareStatement(
        """
        INSERT INTO ml_train_metrics
        (trained_at, model_version, auc, pr_auc, recall_at_1pct_fpr, rows_train, promoted)
        VALUES (?, ?, ?, ?, ?, ?, ?)
        """.trimIndent()
      ).use { ps ->
        ps.setTimestamp(1, Timestamp.from(trainedAt))
        ps.setString(2, modelVersion)
        ps.setDouble(3, auc)
        ps.setDouble(4, prAuc)
        ps.setDouble(5, recallAt1pctFpr)
        ps.setLong(6, rowsTrain)
        ps.setInt(7, promoted)
        ps.executeUpdate()
      }
    }
  }
}
