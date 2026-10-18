package com.example.ab

import org.apache.commons.math3.distribution.ChiSquaredDistribution
import org.apache.commons.math3.distribution.NormalDistribution
import kotlin.math.abs
import kotlin.math.sqrt

enum class MetricType { BINOMIAL, CONTINUOUS }

enum class MetricRole { PRIMARY, SECONDARY, GUARDRAIL }

data class MetricSpec(
    val key: String,
    val type: MetricType,
    val role: MetricRole,
    val minEffect: Double,
    val higherIsBetter: Boolean,
)

data class ArmStats(
    val n: Long,
    val conversions: Double,
    val revenueSum: Double,
    val revenueSumSq: Double,
    val refunds: Double,
    val preSum: Double,
    val preSumSq: Double,
    val revenuePreSum: Double,
)

data class MetricEstimate(
    val metricKey: String,
    val role: MetricRole,
    val controlN: Long,
    val treatmentN: Long,
    val controlMean: Double,
    val treatmentMean: Double,
    val lift: Double,
    val ciLow: Double,
    val ciHigh: Double,
    val pValue: Double,
    val srmPValue: Double,
    val cupedLift: Double?,
    val cupedCiLow: Double?,
    val cupedCiHigh: Double?,
    val cupedPValue: Double?,
    val decision: String,
)

data class ExperimentEstimate(
    val decision: String,
    val srmPValue: Double,
    val metrics: List<MetricEstimate>,
)

private data class MeanDiff(
    val controlMean: Double,
    val treatmentMean: Double,
    val lift: Double,
    val se: Double,
    val ciLow: Double,
    val ciHigh: Double,
    val pValue: Double,
)

object Statistics {
    const val ALPHA = 0.05
    const val SRM_ALPHA = 0.001
    const val MIN_SAMPLE_PER_ARM = 500L

    private val normal = NormalDistribution()
    private val chiSquare = ChiSquaredDistribution(1.0)
    private const val Z_95 = 1.959963984540054

    fun evaluate(
        control: ArmStats,
        treatment: ArmStats,
        metrics: List<MetricSpec>,
        controlWeight: Int,
        treatmentWeight: Int,
    ): ExperimentEstimate {
        val srm = srmPValue(control.n, treatment.n, controlWeight, treatmentWeight)
        val computed = metrics.map { spec -> metricDiff(spec, control, treatment) to spec }
        val sampleOk = control.n >= MIN_SAMPLE_PER_ARM && treatment.n >= MIN_SAMPLE_PER_ARM
        val srmOk = srm >= SRM_ALPHA
        val guardrailStop = computed.any { (diff, spec) -> spec.role == MetricRole.GUARDRAIL && harmful(diff, spec) }
        val primaryShip = computed.any { (diff, spec) -> spec.role == MetricRole.PRIMARY && beneficial(diff, spec) }
        val decision = when {
            !sampleOk || !srmOk -> "hold"
            guardrailStop -> "stop"
            primaryShip -> "ship"
            else -> "hold"
        }
        val rows = computed.map { (diff, spec) ->
            val cuped = if (spec.key == "revenue") cupedRevenue(control, treatment) else null
            MetricEstimate(
                metricKey = spec.key,
                role = spec.role,
                controlN = control.n,
                treatmentN = treatment.n,
                controlMean = diff.controlMean,
                treatmentMean = diff.treatmentMean,
                lift = diff.lift,
                ciLow = diff.ciLow,
                ciHigh = diff.ciHigh,
                pValue = diff.pValue,
                srmPValue = srm,
                cupedLift = cuped?.lift,
                cupedCiLow = cuped?.ciLow,
                cupedCiHigh = cuped?.ciHigh,
                cupedPValue = cuped?.pValue,
                decision = decision,
            )
        }
        return ExperimentEstimate(decision, srm, rows)
    }

    fun srmPValue(controlN: Long, treatmentN: Long, controlWeight: Int, treatmentWeight: Int): Double {
        val total = controlN + treatmentN
        val weight = (controlWeight + treatmentWeight).toDouble()
        if (total == 0L || weight <= 0.0 || controlWeight <= 0 || treatmentWeight <= 0) return 0.0
        val expectedControl = total * controlWeight / weight
        val expectedTreatment = total * treatmentWeight / weight
        val chi = (controlN - expectedControl) * (controlN - expectedControl) / expectedControl +
            (treatmentN - expectedTreatment) * (treatmentN - expectedTreatment) / expectedTreatment
        return (1.0 - chiSquare.cumulativeProbability(chi)).coerceIn(0.0, 1.0)
    }

    private fun metricDiff(spec: MetricSpec, control: ArmStats, treatment: ArmStats): MeanDiff {
        val (cMean, cVar) = moment(spec, control)
        val (tMean, tVar) = moment(spec, treatment)
        return wald(cMean, cVar, control.n, tMean, tVar, treatment.n)
    }

    private fun moment(spec: MetricSpec, arm: ArmStats): Pair<Double, Double> {
        if (arm.n <= 1L) return 0.0 to 0.0
        return when (spec.key) {
            "conversion" -> binomialMoment(arm.conversions, arm.n)
            "refund" -> binomialMoment(arm.refunds, arm.n)
            "revenue" -> continuousMoment(arm.revenueSum, arm.revenueSumSq, arm.n)
            else -> error("unsupported metric ${spec.key}")
        }
    }

    private fun binomialMoment(successes: Double, n: Long): Pair<Double, Double> {
        val p = successes / n
        return p to (p * (1.0 - p))
    }

    private fun continuousMoment(sum: Double, sumSq: Double, n: Long): Pair<Double, Double> {
        val mean = sum / n
        val variance = ((sumSq - sum * sum / n) / (n - 1)).coerceAtLeast(0.0)
        return mean to variance
    }

    private fun wald(
        controlMean: Double,
        controlVar: Double,
        controlN: Long,
        treatmentMean: Double,
        treatmentVar: Double,
        treatmentN: Long,
    ): MeanDiff {
        val lift = treatmentMean - controlMean
        val se = if (controlN == 0L || treatmentN == 0L) {
            0.0
        } else {
            sqrt(controlVar / controlN + treatmentVar / treatmentN)
        }
        if (se < 1e-12) {
            val p = if (abs(lift) < 1e-12) 1.0 else 0.0
            return MeanDiff(controlMean, treatmentMean, lift, se, lift, lift, p)
        }
        val z = lift / se
        val p = (2.0 * (1.0 - normal.cumulativeProbability(abs(z)))).coerceIn(0.0, 1.0)
        return MeanDiff(controlMean, treatmentMean, lift, se, lift - Z_95 * se, lift + Z_95 * se, p)
    }

    private fun beneficial(diff: MeanDiff, spec: MetricSpec): Boolean {
        if (diff.pValue >= ALPHA) return false
        return if (spec.higherIsBetter) diff.lift >= spec.minEffect else diff.lift <= -spec.minEffect
    }

    private fun harmful(diff: MeanDiff, spec: MetricSpec): Boolean {
        if (diff.pValue >= ALPHA) return false
        return if (spec.higherIsBetter) diff.lift <= -spec.minEffect else diff.lift >= spec.minEffect
    }

    private fun cupedRevenue(control: ArmStats, treatment: ArmStats): MeanDiff? {
        val n = control.n + treatment.n
        if (control.n <= 1L || treatment.n <= 1L || n <= 2L) return null
        val sumX = control.preSum + treatment.preSum
        val sumY = control.revenueSum + treatment.revenueSum
        val sumXX = control.preSumSq + treatment.preSumSq
        val sumXY = control.revenuePreSum + treatment.revenuePreSum
        val varX = (sumXX - sumX * sumX / n) / (n - 1)
        if (varX < 1e-9) return null
        val cov = (sumXY - sumX * sumY / n) / (n - 1)
        val theta = cov / varX
        val pooledMeanX = sumX / n

        fun adjustedMean(arm: ArmStats): Double =
            arm.revenueSum / arm.n - theta * (arm.preSum / arm.n - pooledMeanX)

        fun adjustedVariance(arm: ArmStats): Double {
            val nArm = arm.n.toDouble()
            val varY = ((arm.revenueSumSq - arm.revenueSum * arm.revenueSum / nArm) / (nArm - 1)).coerceAtLeast(0.0)
            val varCovariate = ((arm.preSumSq - arm.preSum * arm.preSum / nArm) / (nArm - 1)).coerceAtLeast(0.0)
            val covArm = (arm.revenuePreSum - arm.revenueSum * arm.preSum / nArm) / (nArm - 1)
            return (varY + theta * theta * varCovariate - 2.0 * theta * covArm).coerceAtLeast(0.0)
        }

        return wald(
            adjustedMean(control),
            adjustedVariance(control),
            control.n,
            adjustedMean(treatment),
            adjustedVariance(treatment),
            treatment.n,
        )
    }
}
