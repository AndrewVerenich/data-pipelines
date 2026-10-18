package com.example.ab.analysis

import com.example.ab.ArmStats
import com.example.ab.ClickHouseClient
import com.example.ab.MetricEstimate
import com.example.ab.MetricRole
import com.example.ab.MetricSpec
import com.example.ab.MetricType
import com.example.ab.Statistics
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import org.slf4j.LoggerFactory
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

@Component
class AnalysisLoop(
    properties: AnalysisProperties,
    private val jdbc: JdbcTemplate,
    private val objectMapper: ObjectMapper,
) {
    private val log = LoggerFactory.getLogger(AnalysisLoop::class.java)
    private val clickHouse = ClickHouseClient(
        properties.clickhouseUrl,
        properties.clickhouseUser,
        properties.clickhousePassword,
    )

    @Scheduled(fixedDelayString = "\${ab.interval-ms:60000}", initialDelayString = "\${ab.initial-delay-ms:5000}")
    fun run() {
        try {
            evaluate()
        } catch (ex: Exception) {
            log.warn("analysis pass failed: {}", ex.message)
        }
    }

    private fun evaluate() {
        val specs = loadMetrics()
        val weights = loadWeights()
        val ids = loadIds()
        val arms = loadArms()
        val computedAt = CLICKHOUSE_TIME.format(Instant.now())
        val rows = mutableListOf<String>()
        specs.keys.sorted().forEach { experimentKey ->
            val byVariant = arms[experimentKey] ?: return@forEach
            val control = byVariant["control"] ?: return@forEach
            val treatment = byVariant["treatment"] ?: return@forEach
            val (controlWeight, treatmentWeight) = weights[experimentKey] ?: (50 to 50)
            val estimate = Statistics.evaluate(
                control,
                treatment,
                specs.getValue(experimentKey),
                controlWeight,
                treatmentWeight,
            )
            log.info(
                "{} decision={} srm={} {}",
                experimentKey,
                estimate.decision,
                "%.4f".format(estimate.srmPValue),
                estimate.metrics.joinToString(" ") {
                    "${it.metricKey}=${"%.4f".format(it.lift)}/p=${"%.4f".format(it.pValue)}"
                },
            )
            estimate.metrics.forEach { metric ->
                rows += objectMapper.writeValueAsString(resultRow(ids[experimentKey] ?: 0, experimentKey, metric, computedAt))
            }
        }
        clickHouse.insertJsonEachRow("ab.experiment_results", rows)
    }

    private fun loadMetrics(): Map<String, List<MetricSpec>> {
        val rows = jdbc.query(
            """
            SELECT e.experiment_key, m.metric_key, m.metric_type, m.role, m.min_effect, m.higher_is_better
            FROM metrics m
            JOIN experiments e ON e.experiment_id = m.experiment_id
            WHERE e.status = 'running'
            ORDER BY m.metric_id
            """.trimIndent()
        ) { rs, _ ->
            rs.getString("experiment_key") to MetricSpec(
                key = rs.getString("metric_key"),
                type = MetricType.valueOf(rs.getString("metric_type").uppercase()),
                role = MetricRole.valueOf(rs.getString("role").uppercase()),
                minEffect = rs.getDouble("min_effect"),
                higherIsBetter = rs.getBoolean("higher_is_better"),
            )
        }
        return rows.groupBy({ it.first }, { it.second })
    }

    private fun loadWeights(): Map<String, Pair<Int, Int>> {
        val rows = jdbc.query(
            """
            SELECT e.experiment_key, v.variant_key, v.weight
            FROM variants v
            JOIN experiments e ON e.experiment_id = v.experiment_id
            WHERE e.status = 'running'
            """.trimIndent()
        ) { rs, _ -> Triple(rs.getString("experiment_key"), rs.getString("variant_key"), rs.getInt("weight")) }
        return rows.groupBy { it.first }.mapValues { (_, group) ->
            val control = group.firstOrNull { it.second == "control" }?.third ?: 50
            val treatment = group.firstOrNull { it.second == "treatment" }?.third ?: 50
            control to treatment
        }
    }

    private fun loadIds(): Map<String, Int> {
        return jdbc.query(
            "SELECT experiment_key, experiment_id FROM experiments WHERE status = 'running'"
        ) { rs, _ -> rs.getString("experiment_key") to rs.getInt("experiment_id") }.toMap()
    }

    private fun loadArms(): Map<String, Map<String, ArmStats>> {
        val body = clickHouse.queryJson(
            """
            SELECT
                experiment_key,
                variant_key,
                count() AS n,
                sum(converted) AS conversions,
                sum(revenue) AS revenue_sum,
                sum(revenue * revenue) AS revenue_sumsq,
                sum(refunded) AS refunds,
                sum(pre_revenue) AS pre_sum,
                sum(pre_revenue * pre_revenue) AS pre_sumsq,
                sum(revenue * pre_revenue) AS revenue_pre_sum
            FROM ab.user_level
            GROUP BY experiment_key, variant_key
            """.trimIndent()
        )
        val data = objectMapper.readTree(body).path("data")
        val grouped = mutableMapOf<String, MutableMap<String, ArmStats>>()
        data.forEach { row ->
            val experimentKey = row.path("experiment_key").asText()
            val variantKey = row.path("variant_key").asText()
            grouped.getOrPut(experimentKey) { mutableMapOf() }[variantKey] = ArmStats(
                n = row.num("n").toLong(),
                conversions = row.num("conversions"),
                revenueSum = row.num("revenue_sum"),
                revenueSumSq = row.num("revenue_sumsq"),
                refunds = row.num("refunds"),
                preSum = row.num("pre_sum"),
                preSumSq = row.num("pre_sumsq"),
                revenuePreSum = row.num("revenue_pre_sum"),
            )
        }
        return grouped
    }

    private fun resultRow(
        experimentId: Int,
        experimentKey: String,
        metric: MetricEstimate,
        computedAt: String,
    ): Map<String, Any?> = mapOf(
        "experiment_id" to experimentId,
        "experiment_key" to experimentKey,
        "metric_key" to metric.metricKey,
        "role" to metric.role.name.lowercase(),
        "control_n" to metric.controlN,
        "treatment_n" to metric.treatmentN,
        "control_mean" to metric.controlMean,
        "treatment_mean" to metric.treatmentMean,
        "lift" to metric.lift,
        "ci_low" to metric.ciLow,
        "ci_high" to metric.ciHigh,
        "p_value" to metric.pValue,
        "srm_p_value" to metric.srmPValue,
        "cuped_lift" to metric.cupedLift,
        "cuped_ci_low" to metric.cupedCiLow,
        "cuped_ci_high" to metric.cupedCiHigh,
        "cuped_p_value" to metric.cupedPValue,
        "decision" to metric.decision,
        "computed_at" to computedAt,
    )

    private fun JsonNode.num(field: String): Double {
        val node = path(field)
        return when {
            node.isNumber -> node.asDouble()
            node.isTextual -> node.asText().toDouble()
            else -> 0.0
        }
    }

    private companion object {
        val CLICKHOUSE_TIME: DateTimeFormatter =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS").withZone(ZoneOffset.UTC)
    }
}
