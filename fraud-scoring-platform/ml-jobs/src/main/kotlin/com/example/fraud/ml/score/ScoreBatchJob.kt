package com.example.fraud.ml.score

import ai.catboost.CatBoostModel
import com.example.fraud.ml.clickhouse.ClickHouseClient
import com.example.fraud.ml.features.FeatureConfigLoader
import com.example.fraud.ml.features.decisionFromScore
import com.example.fraud.ml.registry.ModelRegistry
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.kafka.core.KafkaTemplate
import org.springframework.stereotype.Component
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import kotlin.math.exp

@Component
class ScoreBatchJob(
  private val ch: ClickHouseClient,
  private val features: FeatureConfigLoader,
  private val registry: ModelRegistry,
  private val kafkaTemplate: KafkaTemplate<String, Any>,
  @Value("\${app.score-batch-limit:5000}") private val limit: Int
) {
  private val log = LoggerFactory.getLogger(javaClass)
  private val mapper = jacksonObjectMapper()

  fun run() {
    val cfg = features.load()
    val latest = registry.readLatest()
    val featureList = registry.readFeatureList()
    @Suppress("UNCHECKED_CAST")
    val numeric = featureList["numeric_features"] as List<String>
    @Suppress("UNCHECKED_CAST")
    val categorical = featureList["categorical_features"] as List<String>
    @Suppress("UNCHECKED_CAST")
    val thresholdsMap = featureList["decision_thresholds"] as Map<String, Number>
    val thresholds = cfg.decisionThresholds.copy(
      block = thresholdsMap["block"]?.toDouble() ?: cfg.decisionThresholds.block,
      challenge = thresholdsMap["challenge"]?.toDouble() ?: cfg.decisionThresholds.challenge
    )

    val cols = listOf(cfg.idColumn) + numeric + categorical
    val sql = """
      SELECT ${cols.joinToString(", ")}
      FROM feat_transaction_scoring f
      WHERE f.transaction_id NOT IN (SELECT transaction_id FROM landing_scores)
      ORDER BY event_ts DESC
      LIMIT $limit
    """.trimIndent()

    data class Row(val id: String, val numeric: FloatArray, val categorical: Array<String>)

    val rows = ch.query(sql) { rs ->
      Row(
        id = rs.getString(cfg.idColumn),
        numeric = FloatArray(numeric.size) { i -> rs.getDouble(numeric[i]).toFloat() },
        categorical = Array(categorical.size) { i -> rs.getString(categorical[i]) ?: "UNK" }
      )
    }
    if (rows.isEmpty()) {
      log.info("No unscored transactions")
      return
    }

    val model = CatBoostModel.loadModel(registry.latestModelPath().toString())
    val now = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'")
      .withZone(ZoneOffset.UTC)
      .format(Instant.now())
    val topFeatures = (numeric + categorical).take(5)

    rows.forEach { row ->
      val raw = model.predict(row.numeric, row.categorical).get(0, 0)
      val score = 1.0 / (1.0 + exp(-raw))
      val payload = mapOf(
        "transaction_id" to row.id,
        "score" to "%.6f".format(score).toDouble(),
        "decision" to decisionFromScore(score, thresholds),
        "model_version" to latest.model_version,
        "scored_at" to now,
        "top_features" to mapper.writeValueAsString(topFeatures)
      )
      kafkaTemplate.send("payments.scores", row.id, payload)
    }
    model.close()
    log.info("Scored {} transactions with model {}", rows.size, latest.model_version)
  }
}
