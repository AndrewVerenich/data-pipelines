package com.example.fraud.scoring.clickhouse

import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Repository
import java.sql.DriverManager

data class FeatureRow(
  val transactionId: String,
  val numeric: FloatArray,
  val categorical: Array<String>
)

@Repository
class FeatureRepository(
  @Value("\${app.clickhouse.url}") private val url: String,
  @Value("\${app.clickhouse.user}") private val user: String,
  @Value("\${app.clickhouse.password}") private val password: String
) {

  fun findFeatures(
    transactionId: String,
    numericFeatures: List<String>,
    categoricalFeatures: List<String>
  ): FeatureRow? {
    val cols = (listOf("transaction_id") + numericFeatures + categoricalFeatures).joinToString(", ")
    val sql = """
      SELECT $cols
      FROM feat_transaction_scoring
      WHERE transaction_id = ?
      LIMIT 1
    """.trimIndent()
    DriverManager.getConnection(url, user, password).use { conn ->
      conn.prepareStatement(sql).use { ps ->
        ps.setString(1, transactionId)
        ps.executeQuery().use { rs ->
          if (!rs.next()) return null
          return FeatureRow(
            transactionId = rs.getString("transaction_id"),
            numeric = FloatArray(numericFeatures.size) { i -> rs.getDouble(numericFeatures[i]).toFloat() },
            categorical = Array(categoricalFeatures.size) { i -> rs.getString(categoricalFeatures[i]) ?: "UNK" }
          )
        }
      }
    }
  }
}
