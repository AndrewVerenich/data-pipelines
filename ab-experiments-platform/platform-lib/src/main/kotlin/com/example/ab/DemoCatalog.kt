package com.example.ab

data class PlantedEffect(
    val experimentId: Int,
    val experimentKey: String,
    val salt: String,
    val conversionLift: Double,
    val refundLift: Double,
    val revenueLiftRel: Double,
)

object DemoCatalog {
    const val SEED = 42L
    const val BACKFILL_USERS = 8_000
    const val BASE_CONVERSION = 0.20
    const val BASE_REFUND = 0.05
    const val BASE_REVENUE = 40.0
    const val CONTROL = "control"
    const val TREATMENT = "treatment"

    val weights = listOf(CONTROL to 50, TREATMENT to 50)

    val metrics = listOf(
        MetricSpec("conversion", MetricType.BINOMIAL, MetricRole.PRIMARY, minEffect = 0.02, higherIsBetter = true),
        MetricSpec("revenue", MetricType.CONTINUOUS, MetricRole.SECONDARY, minEffect = 0.0, higherIsBetter = true),
        MetricSpec("refund", MetricType.BINOMIAL, MetricRole.GUARDRAIL, minEffect = 0.02, higherIsBetter = false),
    )

    val experiments = listOf(
        PlantedEffect(1, "checkout_cta", "checkout_cta_v1", conversionLift = 0.08, refundLift = 0.0, revenueLiftRel = 0.05),
        PlantedEffect(2, "free_shipping", "free_shipping_v1", conversionLift = 0.0, refundLift = 0.07, revenueLiftRel = 0.0),
        PlantedEffect(3, "aa_checkout", "aa_checkout_v1", conversionLift = 0.0, refundLift = 0.0, revenueLiftRel = 0.0),
    )

    fun effect(experimentKey: String): PlantedEffect? = experiments.find { it.experimentKey == experimentKey }

    fun backfillUserId(experimentKey: String, index: Int): String =
        "$experimentKey:u-${index.toString().padStart(6, '0')}"
}
