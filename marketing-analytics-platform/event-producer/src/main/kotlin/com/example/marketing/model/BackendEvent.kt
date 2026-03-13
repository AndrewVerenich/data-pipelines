package com.example.marketing.model

import java.math.BigDecimal
import java.time.Instant

data class BackendEvent(
  val event_id: String,
  val user_id: Long,
  val event_type: String,
  val order_id: String?,
  val product_id: Int?,
  val amount: BigDecimal?,
  val timestamp: Instant
)
