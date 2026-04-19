package com.example.flink.model

import com.fasterxml.jackson.annotation.JsonIgnoreProperties

@JsonIgnoreProperties(ignoreUnknown = true)
data class FraudRule(
  val ruleId: String = "",
  val ruleType: String = "",
  val eventType: String? = null,
  val maxCount: Int = 0,
  val windowSeconds: Long = 0,
  val active: Boolean = true
)
