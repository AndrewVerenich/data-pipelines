package com.example.flink.model

import com.fasterxml.jackson.annotation.JsonIgnoreProperties

@JsonIgnoreProperties(ignoreUnknown = true)
data class SessionEvent(
  val sessionId: String = "",
  val userId: String = "",
  val eventType: String = "",
  val startTime: Long = 0L,
  val endTime: Long = 0L,
  val eventCount: Int = 0,
  val pages: List<String> = emptyList(),
  val segment: String = "UNKNOWN",
  val durationMs: Long = 0L
)
