package com.example.fraud.ml.features

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.PropertyNamingStrategies
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory
import com.fasterxml.jackson.module.kotlin.KotlinModule
import com.fasterxml.jackson.module.kotlin.readValue
import org.springframework.core.io.ClassPathResource
import org.springframework.stereotype.Component

@JsonIgnoreProperties(ignoreUnknown = true)
data class DecisionThresholds(
  val block: Double = 0.9,
  val challenge: Double = 0.7
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class FeatureConfig(
  val numericFeatures: List<String> = emptyList(),
  val categoricalFeatures: List<String> = emptyList(),
  val labelColumn: String = "label_is_fraud",
  val idColumn: String = "transaction_id",
  val timeColumn: String = "event_ts",
  val decisionThresholds: DecisionThresholds = DecisionThresholds()
) {
  val allFeatures: List<String> get() = numericFeatures + categoricalFeatures
}

@Component
class FeatureConfigLoader {
  private val mapper = ObjectMapper(YAMLFactory())
    .registerModule(KotlinModule.Builder().build())
    .setPropertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE)

  fun load(): FeatureConfig {
    ClassPathResource("feature_config.yml").inputStream.use { input ->
      return mapper.readValue(input)
    }
  }
}

fun decisionFromScore(score: Double, thresholds: DecisionThresholds): String = when {
  score >= thresholds.block -> "BLOCK"
  score >= thresholds.challenge -> "CHALLENGE"
  else -> "ALLOW"
}
