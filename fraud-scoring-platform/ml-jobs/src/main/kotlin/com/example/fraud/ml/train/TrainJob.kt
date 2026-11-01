package com.example.fraud.ml.train

import ai.catboost.CatBoostModel
import com.example.fraud.ml.clickhouse.ClickHouseClient
import com.example.fraud.ml.features.FeatureConfigLoader
import com.example.fraud.ml.metrics.Metrics
import com.example.fraud.ml.registry.LatestPointer
import com.example.fraud.ml.registry.ModelRegistry
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import java.nio.file.Files
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import kotlin.io.path.writeText
import kotlin.math.exp

@Component
class TrainJob(
  private val ch: ClickHouseClient,
  private val features: FeatureConfigLoader,
  private val trainer: CatBoostCliTrainer,
  private val registry: ModelRegistry,
  @Value("\${app.promote-min-auc:0.75}") private val promoteMinAuc: Double
) {
  private val log = LoggerFactory.getLogger(javaClass)

  fun run() {
    val cfg = features.load()
    val cols = listOf(cfg.idColumn, cfg.timeColumn, cfg.labelColumn) + cfg.allFeatures
    val sql = """
      SELECT ${cols.joinToString(", ")}
      FROM feat_transaction_scoring
      WHERE ${cfg.labelColumn} IS NOT NULL
      ORDER BY ${cfg.timeColumn}
    """.trimIndent()

    data class Row(
      val id: String,
      val ts: Instant,
      val label: Int,
      val numeric: FloatArray,
      val categorical: Array<String>
    )

    val rows = ch.query(sql) { rs ->
      val numeric = FloatArray(cfg.numericFeatures.size) { i ->
        rs.getDouble(cfg.numericFeatures[i]).toFloat()
      }
      val categorical = Array(cfg.categoricalFeatures.size) { i ->
        rs.getString(cfg.categoricalFeatures[i]) ?: "UNK"
      }
      val ts = rs.getTimestamp(cfg.timeColumn)?.toInstant()
        ?: Instant.parse(rs.getString(cfg.timeColumn).replace(" ", "T") + "Z")
      Row(
        id = rs.getString(cfg.idColumn),
        ts = ts,
        label = rs.getInt(cfg.labelColumn),
        numeric = numeric,
        categorical = categorical
      )
    }

    if (rows.size < 500 || rows.map { it.label }.toSet().size < 2) {
      throw IllegalStateException("Not enough labeled data to train: rows=${rows.size}")
    }

    val trainEnd = (rows.size * 0.70).toInt()
    val validEnd = (rows.size * 0.85).toInt()
    val train = rows.subList(0, trainEnd)
    val valid = rows.subList(trainEnd, validEnd)
    val test = rows.subList(validEnd, rows.size)

    val workDir = Files.createTempDirectory("catboost-train")
    val learn = workDir.resolve("learn.tsv")
    val testFile = workDir.resolve("test.tsv")
    val cd = workDir.resolve("columns.cd")
    trainer.writeColumnDescription(cd, cfg.numericFeatures.size, cfg.categoricalFeatures.size)

    fun writePool(path: java.nio.file.Path, data: List<Row>) {
      path.writeText(
        data.joinToString("\n") { r ->
          val nums = r.numeric.joinToString("\t") { it.toString() }
          val cats = r.categorical.joinToString("\t")
          "${r.label}\t$nums\t$cats"
        } + "\n"
      )
    }
    // use train+valid for learn; catboost uses test-set for OD
    writePool(learn, train + valid)
    writePool(testFile, test)

    val version = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'")
      .withZone(ZoneOffset.UTC)
      .format(Instant.now())
    registry.ensureRoot()
    val versionDir = registry.versionDir(version)
    val modelPath = versionDir.resolve("model.cbm")

    trainer.fit(
      workDir = workDir,
      learnTsv = learn,
      testTsv = testFile,
      columnDesc = cd,
      modelOut = modelPath,
      catFeatureIndices = cfg.categoricalFeatures.indices.map { it + 1 + cfg.numericFeatures.size }
    )

    val model = CatBoostModel.loadModel(modelPath.toString())
    val yTrue = DoubleArray(test.size) { test[it].label.toDouble() }
    val yScore = DoubleArray(test.size) { i ->
      val pred = model.predict(test[i].numeric, test[i].categorical)
      sigmoid(pred.get(0, 0))
    }
    model.close()

    val metrics = mapOf(
      "rows_total" to rows.size,
      "rows_train" to train.size,
      "rows_valid" to valid.size,
      "rows_test" to test.size,
      "fraud_rate" to rows.map { it.label }.average(),
      "auc" to Metrics.rocAuc(yTrue, yScore),
      "pr_auc" to Metrics.averagePrecision(yTrue, yScore),
      "recall_at_1pct_fpr" to Metrics.recallAtFpr(yTrue, yScore, 0.01),
      "trained_at" to Instant.now().toString()
    )
    log.info("metrics={}", metrics)

    registry.writeJson(versionDir.resolve("metrics.json"), metrics)
    registry.writeJson(
      versionDir.resolve("feature_list.json"),
      mapOf(
        "numeric_features" to cfg.numericFeatures,
        "categorical_features" to cfg.categoricalFeatures,
        "decision_thresholds" to mapOf(
          "block" to cfg.decisionThresholds.block,
          "challenge" to cfg.decisionThresholds.challenge
        )
      )
    )

    val auc = metrics["auc"] as Double
    val promoted = auc >= promoteMinAuc
    registry.promote(
      versionDir,
      LatestPointer(
        model_version = version,
        promoted = promoted,
        auc = auc,
        path = modelPath.toString()
      )
    )
    ch.insertTrainMetrics(
      trainedAt = Instant.now(),
      modelVersion = version,
      auc = auc,
      prAuc = metrics["pr_auc"] as Double,
      recallAt1pctFpr = metrics["recall_at_1pct_fpr"] as Double,
      rowsTrain = train.size.toLong(),
      promoted = if (promoted) 1 else 0
    )
    log.info("Model {} published (promoted={})", version, promoted)
  }

  private fun sigmoid(x: Double): Double = 1.0 / (1.0 + exp(-x))
}
