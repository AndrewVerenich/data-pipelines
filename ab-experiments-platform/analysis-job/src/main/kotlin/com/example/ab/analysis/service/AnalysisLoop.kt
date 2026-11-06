package com.example.ab.analysis.service

import com.example.ab.analysis.repository.ExperimentConfigRepository
import com.example.ab.analysis.repository.UserLevelRepository
import com.example.ab.clickhouse.ClickHouseClient
import com.example.ab.stats.MetricEstimate
import com.example.ab.stats.Statistics
import com.fasterxml.jackson.databind.ObjectMapper
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

@Component
class AnalysisLoop(
  private val experimentConfigRepository: ExperimentConfigRepository,
  private val userLevelRepository: UserLevelRepository,
  private val clickHouse: ClickHouseClient,
  private val objectMapper: ObjectMapper,
) {
  private val log = LoggerFactory.getLogger(AnalysisLoop::class.java)

  @Scheduled(fixedDelayString = "\${ab.interval-ms:60000}", initialDelayString = "\${ab.initial-delay-ms:5000}")
  fun run() {
    try {
      evaluate()
    } catch (ex: Exception) {
      log.warn("analysis pass failed: {}", ex.message)
    }
  }

  private fun evaluate() {
    val specs = experimentConfigRepository.loadMetrics()
    val weights = experimentConfigRepository.loadWeights()
    val ids = experimentConfigRepository.loadIds()
    val arms = userLevelRepository.loadArms()
    val computedAt = CLICKHOUSE_TIME.format(Instant.now())
    val rows = mutableListOf<String>()
    specs.keys.sorted().forEach { experimentKey ->
      val byVariant = arms[experimentKey] ?: return@forEach
      val control = byVariant["control"] ?: return@forEach
      val treatment = byVariant["treatment"] ?: return@forEach
      val (controlWeight, treatmentWeight) = weights[experimentKey] ?: (50 to 50)
      val estimate = Statistics.evaluate(
        control,
        treatment,
        specs.getValue(experimentKey),
        controlWeight,
        treatmentWeight,
      )
      log.info(
        "{} decision={} srm={} {}",
        experimentKey,
        estimate.decision,
        "%.4f".format(estimate.srmPValue),
        estimate.metrics.joinToString(" ") {
          "${it.metricKey}=${"%.4f".format(it.lift)}/p=${"%.4f".format(it.pValue)}"
        },
      )
      estimate.metrics.forEach { metric ->
        rows += objectMapper.writeValueAsString(resultRow(ids[experimentKey] ?: 0, experimentKey, metric, computedAt))
      }
    }
    clickHouse.insertJsonEachRow("ab.experiment_results", rows)
  }

  private fun resultRow(
    experimentId: Int,
    experimentKey: String,
    metric: MetricEstimate,
    computedAt: String,
  ): Map<String, Any?> = mapOf(
    "experiment_id" to experimentId,
    "experiment_key" to experimentKey,
    "metric_key" to metric.metricKey,
    "role" to metric.role.name.lowercase(),
    "control_n" to metric.controlN,
    "treatment_n" to metric.treatmentN,
    "control_mean" to metric.controlMean,
    "treatment_mean" to metric.treatmentMean,
    "lift" to metric.lift,
    "ci_low" to metric.ciLow,
    "ci_high" to metric.ciHigh,
    "p_value" to metric.pValue,
    "srm_p_value" to metric.srmPValue,
    "cuped_lift" to metric.cupedLift,
    "cuped_ci_low" to metric.cupedCiLow,
    "cuped_ci_high" to metric.cupedCiHigh,
    "cuped_p_value" to metric.cupedPValue,
    "decision" to metric.decision,
    "computed_at" to computedAt,
  )

  private companion object {
    val CLICKHOUSE_TIME: DateTimeFormatter =
      DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS").withZone(ZoneOffset.UTC)
  }
}
