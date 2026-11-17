package com.example.ab.simulator.demo

object DemoCatalog {
  const val SEED = 42L
  const val BASE_CONVERSION = 0.20
  const val BASE_REFUND = 0.05
  const val BASE_REVENUE = 40.0
  const val TREATMENT = "treatment"

  val experiments = listOf(
    PlantedEffect(1, "checkout_cta", "checkout_cta_v1", conversionLift = 0.08, refundLift = 0.0, revenueLiftRel = 0.05),
    PlantedEffect(
      2,
      "free_shipping",
      "free_shipping_v1",
      conversionLift = 0.0,
      refundLift = 0.07,
      revenueLiftRel = 0.0
    ),
    PlantedEffect(3, "aa_checkout", "aa_checkout_v1", conversionLift = 0.0, refundLift = 0.0, revenueLiftRel = 0.0),
  )
}
