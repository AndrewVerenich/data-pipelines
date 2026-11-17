package com.example.ab.simulator.model

import com.fasterxml.jackson.annotation.JsonProperty

data class AssignResponse(
  @JsonProperty("experiment_key") val experimentKey: String = "",
  @JsonProperty("experiment_id") val experimentId: Int = 0,
  @JsonProperty("user_id") val userId: String = "",
  @JsonProperty("in_experiment") val inExperiment: Boolean = false,
  @JsonProperty("variant_key") val variantKey: String? = null,
  val bucket: Int = 0,
  @JsonProperty("assigned_at") val assignedAt: String? = null,
)
