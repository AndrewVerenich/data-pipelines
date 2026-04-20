package com.example.banking.model

import java.time.Instant
import java.time.LocalDate

data class Customer(
  val customer_id: Long,
  val first_name: String,
  val last_name: String,
  val email: String,
  val age: Int,
  val city: String,
  val country: String,
  val registration_date: LocalDate,
  val timestamp: Instant
)
