package com.example.marketing.model

import java.math.BigDecimal
import java.time.Instant

data class WebsiteEvent(
  val event_id: String,
  val user_id: Long,
  val event_type: String,
  val page_url: String,
  val product_id: Int?,
  val revenue: BigDecimal?,
  val session_id: String,
  val timestamp: Instant
)
