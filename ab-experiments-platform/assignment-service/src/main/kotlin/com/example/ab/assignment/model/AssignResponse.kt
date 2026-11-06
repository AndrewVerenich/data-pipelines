package com.example.ab.assignment.model

import com.fasterxml.jackson.annotation.JsonProperty

data class AssignResponse(
  @JsonProperty("experiment_key") val experimentKey: String,
  @JsonProperty("experiment_id") val experimentId: Int,
  @JsonProperty("user_id") val userId: String,
  @JsonProperty("in_experiment") val inExperiment: Boolean,
  @JsonProperty("variant_key") val variantKey: String?,
  val bucket: Int,
  @JsonProperty("exposed_at") val exposedAt: Double?,
)
