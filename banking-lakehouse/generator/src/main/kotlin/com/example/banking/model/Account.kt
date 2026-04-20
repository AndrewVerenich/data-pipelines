package com.example.banking.model

import java.time.Instant
import java.time.LocalDate

data class Account(
  val account_id: Long,
  val customer_id: Long,
  val account_type: String,
  val currency: String,
  val opened_at: LocalDate,
  val timestamp: Instant
)
