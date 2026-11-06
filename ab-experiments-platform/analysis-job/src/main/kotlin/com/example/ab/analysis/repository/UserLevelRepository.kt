package com.example.ab.analysis.repository

import com.example.ab.clickhouse.ClickHouseClient
import com.example.ab.stats.ArmStats
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import org.springframework.stereotype.Repository

@Repository
class UserLevelRepository(
  private val clickHouse: ClickHouseClient,
  private val objectMapper: ObjectMapper,
) {
  fun loadArms(): Map<String, Map<String, ArmStats>> {
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

  private fun JsonNode.num(field: String): Double {
    val node = path(field)
    return when {
      node.isNumber -> node.asDouble()
      node.isTextual -> node.asText().toDouble()
      else -> 0.0
    }
  }
}
