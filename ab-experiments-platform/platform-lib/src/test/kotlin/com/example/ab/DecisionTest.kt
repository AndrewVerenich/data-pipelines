package com.example.ab

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class DecisionTest {
    @Test
    fun `planted experiments resolve to ship stop and hold`() {
        val expected = mapOf(
            "checkout_cta" to "ship",
            "free_shipping" to "stop",
            "aa_checkout" to "hold",
        )
        DemoCatalog.experiments.forEach { effect ->
            val users = (0 until DemoCatalog.BACKFILL_USERS).map { OutcomeSampler.backfillUser(effect, it) }
            val estimate = Statistics.evaluate(
                control = aggregate(users, DemoCatalog.CONTROL),
                treatment = aggregate(users, DemoCatalog.TREATMENT),
                metrics = DemoCatalog.metrics,
                controlWeight = 50,
                treatmentWeight = 50,
            )
            val summary = estimate.metrics.joinToString("; ") {
                "${it.metricKey} lift=${"%.4f".format(it.lift)} p=${"%.4f".format(it.pValue)}"
            }
            assertEquals(expected[effect.experimentKey], estimate.decision, "${effect.experimentKey}: $summary")
            assertTrue(estimate.srmPValue > Statistics.SRM_ALPHA, "srm=${estimate.srmPValue}")
        }
    }

    @Test
    fun `cuped tightens the revenue interval when the covariate is informative`() {
        val effect = DemoCatalog.effect("checkout_cta")!!
        val users = (0 until DemoCatalog.BACKFILL_USERS).map { OutcomeSampler.backfillUser(effect, it) }
        val estimate = Statistics.evaluate(
            control = aggregate(users, DemoCatalog.CONTROL),
            treatment = aggregate(users, DemoCatalog.TREATMENT),
            metrics = DemoCatalog.metrics,
            controlWeight = 50,
            treatmentWeight = 50,
        )
        val revenue = estimate.metrics.first { it.metricKey == "revenue" }
        val cupedLow = assertNotNull(revenue.cupedCiLow)
        val cupedHigh = assertNotNull(revenue.cupedCiHigh)
        val rawWidth = revenue.ciHigh - revenue.ciLow
        val cupedWidth = cupedHigh - cupedLow
        assertTrue(cupedWidth < rawWidth, "raw=$rawWidth cuped=$cupedWidth")
    }

    private fun aggregate(users: List<SampledUser>, variant: String): ArmStats {
        val arm = users.filter { it.variantKey == variant }
        return ArmStats(
            n = arm.size.toLong(),
            conversions = arm.count { it.converted }.toDouble(),
            revenueSum = arm.sumOf { it.revenue },
            revenueSumSq = arm.sumOf { it.revenue * it.revenue },
            refunds = arm.count { it.refunded }.toDouble(),
            preSum = arm.sumOf { it.preRevenue },
            preSumSq = arm.sumOf { it.preRevenue * it.preRevenue },
            revenuePreSum = arm.sumOf { it.revenue * it.preRevenue },
        )
    }
}
