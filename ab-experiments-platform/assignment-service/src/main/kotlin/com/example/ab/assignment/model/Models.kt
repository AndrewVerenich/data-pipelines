package com.example.ab.assignment.model

import com.fasterxml.jackson.annotation.JsonProperty
import jakarta.validation.constraints.Max
import jakarta.validation.constraints.Min
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotEmpty

data class VariantWeight(val key: String, val weight: Int)

data class ExperimentConfig(
  val id: Int,
  val key: String,
  val name: String,
  val status: String,
  val salt: String,
  val trafficPct: Int,
  val attributionWindowSeconds: Int,
  val variants: List<VariantWeight>,
)

data class CreateExperimentRequest(
  @field:NotBlank @JsonProperty("experiment_key") val experimentKey: String,
  @field:NotBlank val name: String,
  @field:NotBlank val salt: String,
  @field:Min(0) @field:Max(100) @JsonProperty("traffic_pct") val trafficPct: Int = 100,
  @JsonProperty("attribution_window_seconds") val attributionWindowSeconds: Int = 3600,
  @field:NotEmpty val variants: List<VariantWeightRequest>,
  @field:NotEmpty val metrics: List<MetricRequest>,
)

data class VariantWeightRequest(
  @field:NotBlank val key: String,
  @field:Min(1) val weight: Int,
)

data class MetricRequest(
  @field:NotBlank @JsonProperty("metric_key") val metricKey: String,
  @field:NotBlank @JsonProperty("metric_type") val metricType: String,
  @field:NotBlank val role: String,
  @JsonProperty("min_effect") val minEffect: Double,
  @JsonProperty("higher_is_better") val higherIsBetter: Boolean,
)

data class ExperimentResponse(
  @JsonProperty("experiment_id") val experimentId: Int,
  @JsonProperty("experiment_key") val experimentKey: String,
  val name: String,
  val status: String,
  val salt: String,
  @JsonProperty("traffic_pct") val trafficPct: Int,
  @JsonProperty("attribution_window_seconds") val attributionWindowSeconds: Int,
  val variants: List<VariantWeight>,
)

data class AssignResponse(
  @JsonProperty("experiment_key") val experimentKey: String,
  @JsonProperty("experiment_id") val experimentId: Int,
  @JsonProperty("user_id") val userId: String,
  @JsonProperty("in_experiment") val inExperiment: Boolean,
  @JsonProperty("variant_key") val variantKey: String?,
  val bucket: Int,
  @JsonProperty("assigned_at") val assignedAt: String?,
)

data class OutcomeRequest(
  @field:NotBlank @JsonProperty("experiment_key") val experimentKey: String,
  @field:NotBlank @JsonProperty("user_id") val userId: String,
  @field:NotBlank @JsonProperty("event_type") val eventType: String,
  val value: Double,
  @JsonProperty("event_time") val eventTime: String? = null,
  @JsonProperty("event_id") val eventId: String? = null,
)

data class OutcomeBatchRequest(
  @field:NotEmpty val events: List<OutcomeRequest>,
)

data class AssignmentRow(
  val experimentId: Int,
  val experimentKey: String,
  val userId: String,
  val variantKey: String,
  val bucket: Int,
  val assignedAt: java.time.Instant,
)
