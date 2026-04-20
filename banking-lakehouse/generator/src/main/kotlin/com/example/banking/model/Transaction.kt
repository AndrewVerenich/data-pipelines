package com.example.banking.model

import java.math.BigDecimal
import java.time.Instant

data class Transaction(
  val transaction_id: String,
  val account_id: Long,
  val amount: BigDecimal,
  val currency: String,
  val category: String,
  val merchant: String,
  val channel: String,
  val transaction_type: String,
  val timestamp: Instant
)
