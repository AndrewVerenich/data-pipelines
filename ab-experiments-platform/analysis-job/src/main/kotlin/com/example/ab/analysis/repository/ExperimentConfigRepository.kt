package com.example.ab.analysis.repository

import com.example.ab.stats.MetricRole
import com.example.ab.stats.MetricSpec
import com.example.ab.stats.MetricType
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Repository

@Repository
class ExperimentConfigRepository(private val jdbc: JdbcTemplate) {
  fun loadMetrics(): Map<String, List<MetricSpec>> {
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

  fun loadWeights(): Map<String, Pair<Int, Int>> {
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

  fun loadIds(): Map<String, Int> {
    return jdbc.query(
      "SELECT experiment_key, experiment_id FROM experiments WHERE status = 'running'"
    ) { rs, _ -> rs.getString("experiment_key") to rs.getInt("experiment_id") }.toMap()
  }
}
