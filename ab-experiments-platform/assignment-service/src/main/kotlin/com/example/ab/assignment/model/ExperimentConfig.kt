package com.example.ab.assignment.model

data class VariantWeight(val key: String, val weight: Int)

data class ExperimentConfig(
  val id: Int,
  val key: String,
  val salt: String,
  val trafficPct: Int,
  val variants: List<VariantWeight>,
)
