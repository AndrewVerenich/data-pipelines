package com.example.ab.assignment.model

import com.fasterxml.jackson.annotation.JsonProperty

data class ExposureEvent(
  @JsonProperty("exposure_id") val exposureId: String,
  @JsonProperty("experiment_id") val experimentId: Int,
  @JsonProperty("experiment_key") val experimentKey: String,
  @JsonProperty("variant_key") val variantKey: String,
  @JsonProperty("user_id") val userId: String,
  val bucket: Int,
  @JsonProperty("exposed_at") val exposedAt: Double,
  @JsonProperty("exposure_version") val exposureVersion: Long,
)
