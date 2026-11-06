package com.example.ab.stats

enum class MetricType { BINOMIAL, CONTINUOUS }

enum class MetricRole { PRIMARY, SECONDARY, GUARDRAIL }

data class MetricSpec(
  val key: String,
  val type: MetricType,
  val role: MetricRole,
  val minEffect: Double,
  val higherIsBetter: Boolean,
)

data class ArmStats(
  val n: Long,
  val conversions: Double,
  val revenueSum: Double,
  val revenueSumSq: Double,
  val refunds: Double,
  val preSum: Double,
  val preSumSq: Double,
  val revenuePreSum: Double,
)

data class MetricEstimate(
  val metricKey: String,
  val role: MetricRole,
  val controlN: Long,
  val treatmentN: Long,
  val controlMean: Double,
  val treatmentMean: Double,
  val lift: Double,
  val ciLow: Double,
  val ciHigh: Double,
  val pValue: Double,
  val srmPValue: Double,
  val cupedLift: Double?,
  val cupedCiLow: Double?,
  val cupedCiHigh: Double?,
  val cupedPValue: Double?,
  val decision: String,
)

data class ExperimentEstimate(
  val decision: String,
  val srmPValue: Double,
  val metrics: List<MetricEstimate>,
)
