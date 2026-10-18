package com.example.ab

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class BucketerTest {
    @Test
    fun `bucket is stable and stays inside the hash space`() {
        val first = Bucketer.bucket("checkout_cta_v1", "checkout_cta:u-000042")
        val second = Bucketer.bucket("checkout_cta_v1", "checkout_cta:u-000042")
        assertEquals(first, second)
        assertTrue(first in 0 until Bucketer.BUCKETS)
    }

    @Test
    fun `equal weights split the backfill population near fifty fifty`() {
        val effect = DemoCatalog.experiments.first()
        var control = 0
        var treatment = 0
        repeat(DemoCatalog.BACKFILL_USERS) { index ->
            val user = OutcomeSampler.backfillUser(effect, index)
            when (user.variantKey) {
                DemoCatalog.CONTROL -> control++
                DemoCatalog.TREATMENT -> treatment++
            }
        }
        assertTrue(control > 3_500, "control=$control")
        assertTrue(treatment > 3_500, "treatment=$treatment")
    }

    @Test
    fun `earlier exposure keeps the higher version`() {
        assertTrue(ExposureVersion.of(1_000) > ExposureVersion.of(2_000))
    }
}
