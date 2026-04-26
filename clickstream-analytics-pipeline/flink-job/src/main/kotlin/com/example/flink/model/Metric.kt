package com.example.flink.model

import com.fasterxml.jackson.annotation.JsonIgnoreProperties

@JsonIgnoreProperties(ignoreUnknown = true)
data class Metric(
  val metric: String = "",
  val key: String = "",
  val windowStart: Long = 0L,
  val windowEnd: Long = 0L,
  val value: Double = 0.0
)

data class KV(val key: String, val value: Long)
