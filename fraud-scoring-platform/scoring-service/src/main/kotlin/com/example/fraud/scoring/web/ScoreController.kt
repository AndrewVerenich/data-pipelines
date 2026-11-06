package com.example.fraud.scoring.web

import com.example.fraud.scoring.clickhouse.FeatureRepository
import com.example.fraud.scoring.service.ModelService
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import org.springframework.http.HttpStatus
import org.springframework.kafka.core.KafkaTemplate
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.server.ResponseStatusException
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import kotlin.math.exp
import kotlin.system.measureTimeMillis

data class ScoreRequest(
  val transaction_id: String,
  val publish: Boolean = true
)

data class ScoreResponse(
  val transaction_id: String,
  val score: Double,
  val decision: String,
  val model_version: String,
  val top_features: List<String>,
  val latency_ms: Double
)

@RestController
class ScoreController(
  private val modelService: ModelService,
  private val features: FeatureRepository,
  private val kafkaTemplate: KafkaTemplate<String, Any>
) {
  private val mapper = jacksonObjectMapper()

  @GetMapping("/health")
  fun health(): Map<String, Any?> {
    return try {
      val m = modelService.current()
      mapOf("status" to "ok", "model_version" to m.version, "loaded_at" to m.loadedAt)
    } catch (_: Exception) {
      mapOf("status" to "degraded", "model_version" to null, "loaded_at" to null)
    }
  }

  @PostMapping("/reload")
  fun reload(): Map<String, String> {
    modelService.reload()
    return mapOf("model_version" to modelService.current().version)
  }

  @PostMapping("/score")
  fun score(@RequestBody req: ScoreRequest): ScoreResponse {
    val loaded = try {
      modelService.current()
    } catch (ex: Exception) {
      throw ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Model not ready: ${ex.message}")
    }

    lateinit var response: ScoreResponse
    val elapsed = measureTimeMillis {
      val row = features.findFeatures(req.transaction_id, loaded.numericFeatures, loaded.categoricalFeatures)
        ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "Features not found for transaction_id")

      val raw = loaded.model.predict(row.numeric, row.categorical).get(0, 0)
      val score = 1.0 / (1.0 + exp(-raw))
      val decision = when {
        score >= loaded.thresholds.block -> "BLOCK"
        score >= loaded.thresholds.challenge -> "CHALLENGE"
        else -> "ALLOW"
      }
      val top = (loaded.numericFeatures + loaded.categoricalFeatures).take(5)
      val scoredAt = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'")
        .withZone(ZoneOffset.UTC)
        .format(Instant.now())

      if (req.publish) {
        kafkaTemplate.send(
          "payments.scores",
          req.transaction_id,
          mapOf(
            "transaction_id" to req.transaction_id,
            "score" to "%.6f".format(score).toDouble(),
            "decision" to decision,
            "model_version" to loaded.version,
            "scored_at" to scoredAt,
            "top_features" to mapper.writeValueAsString(top)
          )
        )
      }

      response = ScoreResponse(
        transaction_id = req.transaction_id,
        score = "%.6f".format(score).toDouble(),
        decision = decision,
        model_version = loaded.version,
        top_features = top,
        latency_ms = 0.0
      )
    }
    return response.copy(latency_ms = elapsed.toDouble())
  }
}
