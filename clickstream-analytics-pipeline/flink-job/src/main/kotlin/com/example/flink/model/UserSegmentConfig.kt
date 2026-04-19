package com.example.flink.model

import com.fasterxml.jackson.annotation.JsonIgnoreProperties

@JsonIgnoreProperties(ignoreUnknown = true)
data class UserSegmentConfig(
  val userId: String = "",
  val segment: String = "UNKNOWN",
  val updatedAt: Long = 0L
)
