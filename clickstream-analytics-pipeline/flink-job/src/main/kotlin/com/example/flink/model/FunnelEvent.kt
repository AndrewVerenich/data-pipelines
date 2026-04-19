package com.example.flink.model

import com.fasterxml.jackson.annotation.JsonIgnoreProperties

@JsonIgnoreProperties(ignoreUnknown = true)
data class FunnelEvent(
  val userId: String = "",
  val step: String = "",
  val previousStep: String? = null,
  val stepTimestamp: Long = 0L,
  val funnelStartTime: Long = 0L,
  val elapsedMs: Long = 0L,
  val segment: String = "UNKNOWN"
)
