package com.example.flink.model

import com.fasterxml.jackson.annotation.JsonIgnoreProperties

@JsonIgnoreProperties(ignoreUnknown = true)
data class ClickstreamEvent(
  val userId: String = "",
  val eventType: String = "",
  val page: String = "",
  val productId: String? = null,
  val category: String? = null,
  val price: Double? = null,
  val quantity: Int? = null,
  val searchQuery: String? = null,
  val referrer: String? = null,
  val timestamp: Long = 0L
) {
  fun isValid(): Boolean =
    userId.isNotBlank() &&
      eventType.isNotBlank() &&
      page.isNotBlank() &&
      timestamp > 0
}
