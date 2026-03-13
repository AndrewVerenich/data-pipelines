package com.example.marketing.model

import java.math.BigDecimal
import java.time.Instant

data class AdEvent(
  val event_id: String,
  val campaign_id: Int,
  val platform: String,
  val event_type: String,
  val cost: BigDecimal,
  val revenue: BigDecimal?,
  val user_id: Long?,
  val timestamp: Instant
)
