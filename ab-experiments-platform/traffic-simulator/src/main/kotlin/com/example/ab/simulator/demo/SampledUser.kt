package com.example.ab.simulator.demo

data class SampledUser(
  val userId: String,
  val bucket: Int,
  val variantKey: String,
  val converted: Boolean,
  val revenue: Double,
  val refunded: Boolean,
  val preRevenue: Double,
)
