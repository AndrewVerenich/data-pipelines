package com.example.flink.model

import com.fasterxml.jackson.annotation.JsonIgnoreProperties

@JsonIgnoreProperties(ignoreUnknown = true)
data class EnrichedEvent(
  val userId: String = "",
  val eventType: String = "",
  val page: String = "",
  val productId: String? = null,
  val category: String? = null,
  val price: Double? = null,
  val quantity: Int? = null,
  val searchQuery: String? = null,
  val referrer: String? = null,
  val timestamp: Long = 0L,
  val userSegment: String = "UNKNOWN"
) {
  companion object {
    fun from(raw: ClickstreamEvent, segment: String): EnrichedEvent = EnrichedEvent(
      userId = raw.userId,
      eventType = raw.eventType,
      page = raw.page,
      productId = raw.productId,
      category = raw.category,
      price = raw.price,
      quantity = raw.quantity,
      searchQuery = raw.searchQuery,
      referrer = raw.referrer,
      timestamp = raw.timestamp,
      userSegment = segment
    )
  }
}
