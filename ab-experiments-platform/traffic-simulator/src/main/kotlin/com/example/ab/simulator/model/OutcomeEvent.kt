package com.example.ab.simulator.model

import com.fasterxml.jackson.annotation.JsonProperty

data class OutcomeEvent(
  @JsonProperty("event_id") val eventId: String,
  @JsonProperty("user_id") val userId: String,
  @JsonProperty("event_type") val eventType: String,
  val value: Double,
  @JsonProperty("event_time") val eventTime: Double,
)
