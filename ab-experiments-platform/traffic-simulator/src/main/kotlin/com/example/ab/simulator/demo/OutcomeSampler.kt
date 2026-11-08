package com.example.ab.simulator.demo

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.ln
import kotlin.math.sqrt
import kotlin.random.Random

object OutcomeSampler {
  fun liveUser(effect: PlantedEffect, userId: String, variantKey: String, bucket: Int): SampledUser {
    val rng = Random(DemoCatalog.SEED xor userId.hashCode().toLong() xor effect.experimentId.toLong())
    val treated = variantKey == DemoCatalog.TREATMENT
    val pConv = (DemoCatalog.BASE_CONVERSION + if (treated) effect.conversionLift else 0.0).coerceIn(0.0, 1.0)
    val pRefund = (DemoCatalog.BASE_REFUND + if (treated) effect.refundLift else 0.0).coerceIn(0.0, 1.0)
    val latent = rng.nextGaussian()
    val preRevenue = (25.0 + 12.0 * latent + 5.0 * rng.nextGaussian()).coerceAtLeast(0.0)
    val converted = rng.nextDouble() < pConv
    val revenue = if (converted) {
      val amount = (DemoCatalog.BASE_REVENUE + 15.0 * latent + 8.0 * rng.nextGaussian()).coerceAtLeast(1.0)
      amount * (1.0 + if (treated) effect.revenueLiftRel else 0.0)
    } else {
      0.0
    }
    val refunded = rng.nextDouble() < pRefund
    return SampledUser(userId, bucket, variantKey, converted, revenue, refunded, preRevenue)
  }
}

private fun Random.nextGaussian(): Double {
  val u1 = nextDouble().coerceAtLeast(1e-12)
  val u2 = nextDouble()
  return sqrt(-2.0 * ln(u1)) * cos(2.0 * PI * u2)
}
