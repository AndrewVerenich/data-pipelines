package com.example.fraud.scoring.service

import ai.catboost.CatBoostModel
import com.example.fraud.scoring.config.DecisionThresholds
import com.example.fraud.scoring.config.FeatureConfig
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import jakarta.annotation.PostConstruct
import jakarta.annotation.PreDestroy
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import java.nio.file.Path
import java.util.concurrent.atomic.AtomicReference

data class LoadedModel(
  val model: CatBoostModel,
  val version: String,
  val numericFeatures: List<String>,
  val categoricalFeatures: List<String>,
  val thresholds: DecisionThresholds,
  val loadedAt: String
)

@Service
class ModelService(
  private val featureConfig: FeatureConfig,
  @Value("\${app.model-root}") private val modelRoot: String
) {
  private val log = LoggerFactory.getLogger(javaClass)
  private val mapper = jacksonObjectMapper()
  private val state = AtomicReference<LoadedModel?>(null)

  @PostConstruct
  fun init() {
    repeat(30) {
      try {
        reload()
        return
      } catch (ex: Exception) {
        log.warn("Waiting for model artifact: {}", ex.message)
        Thread.sleep(5000)
      }
    }
    log.warn("Started without model; /score will fail until train completes")
  }

  @PreDestroy
  fun shutdown() {
    state.getAndSet(null)?.model?.close()
  }

  fun reload() {
    val root = Path.of(modelRoot)
    val latest = mapper.readTree(root.resolve("latest.json").toFile())
    val featureList = mapper.readTree(root.resolve("latest").resolve("feature_list.json").toFile())
    val version = latest.get("model_version").asText()
    val numeric = featureList.get("numeric_features").map { it.asText() }
    val categorical = featureList.get("categorical_features").map { it.asText() }
    val thresholdsNode = featureList.get("decision_thresholds")
    val thresholds = DecisionThresholds(
      block = thresholdsNode?.get("block")?.asDouble() ?: featureConfig.decisionThresholds.block,
      challenge = thresholdsNode?.get("challenge")?.asDouble() ?: featureConfig.decisionThresholds.challenge
    )
    val model = CatBoostModel.loadModel(root.resolve("latest").resolve("model.cbm").toString())
    state.getAndSet(
      LoadedModel(
        model = model,
        version = version,
        numericFeatures = numeric,
        categoricalFeatures = categorical,
        thresholds = thresholds,
        loadedAt = java.time.Instant.now().toString()
      )
    )?.model?.close()
    log.info("Loaded model version={}", version)
  }

  fun current(): LoadedModel =
    state.get() ?: throw IllegalStateException("Model not ready")
}
