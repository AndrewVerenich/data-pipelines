package com.example.flink.model

import com.fasterxml.jackson.annotation.JsonIgnoreProperties

@JsonIgnoreProperties(ignoreUnknown = true)
data class FraudAlert(
  val userId: String = "",
  val ruleId: String = "",
  val ruleType: String = "",
  val eventType: String = "",
  val eventCount: Int = 0,
  val windowStart: Long = 0L,
  val windowEnd: Long = 0L,
  val timestamp: Long = 0L,
  val segment: String = "UNKNOWN"
)
