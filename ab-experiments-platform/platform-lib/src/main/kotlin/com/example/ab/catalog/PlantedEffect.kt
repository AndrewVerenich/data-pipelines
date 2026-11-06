package com.example.ab.catalog

data class PlantedEffect(
  val experimentId: Int,
  val experimentKey: String,
  val salt: String,
  val conversionLift: Double,
  val refundLift: Double,
  val revenueLiftRel: Double,
)
