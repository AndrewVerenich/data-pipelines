package com.example.ab.assignment.repository

import com.example.ab.assignment.model.CreateExperimentRequest
import com.example.ab.assignment.model.ExperimentConfig
import com.example.ab.assignment.model.ExperimentResponse
import com.example.ab.assignment.model.VariantWeight
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.jdbc.support.GeneratedKeyHolder
import org.springframework.stereotype.Repository
import org.springframework.transaction.annotation.Transactional
import java.sql.Statement
import java.sql.Timestamp
import java.time.Instant

@Repository
class ExperimentRepository(private val jdbc: JdbcTemplate) {
  fun findConfig(experimentKey: String): ExperimentConfig? {
    val rows = jdbc.query(
      """
      SELECT e.experiment_id, e.experiment_key, e.name, e.status, e.salt, e.traffic_pct,
             e.attribution_window_seconds, v.variant_key, v.weight
      FROM experiments e
      JOIN variants v ON v.experiment_id = e.experiment_id
      WHERE e.experiment_key = ?
      ORDER BY v.variant_key
      """.trimIndent(),
      { rs, _ ->
        Triple(
          ExperimentConfig(
            id = rs.getInt("experiment_id"),
            key = rs.getString("experiment_key"),
            name = rs.getString("name"),
            status = rs.getString("status"),
            salt = rs.getString("salt"),
            trafficPct = rs.getInt("traffic_pct"),
            attributionWindowSeconds = rs.getInt("attribution_window_seconds"),
            variants = emptyList(),
          ),
          rs.getString("variant_key"),
          rs.getInt("weight"),
        )
      },
      experimentKey,
    )
    if (rows.isEmpty()) return null
    val head = rows.first().first
    return head.copy(variants = rows.map { VariantWeight(it.second, it.third) })
  }

  fun list(): List<ExperimentResponse> {
    val configs = jdbc.query(
      """
      SELECT e.experiment_id, e.experiment_key, e.name, e.status, e.salt, e.traffic_pct,
             e.attribution_window_seconds, v.variant_key, v.weight
      FROM experiments e
      JOIN variants v ON v.experiment_id = e.experiment_id
      ORDER BY e.experiment_id, v.variant_key
      """.trimIndent()
    ) { rs, _ ->
      Row(
        id = rs.getInt("experiment_id"),
        key = rs.getString("experiment_key"),
        name = rs.getString("name"),
        status = rs.getString("status"),
        salt = rs.getString("salt"),
        trafficPct = rs.getInt("traffic_pct"),
        attributionWindowSeconds = rs.getInt("attribution_window_seconds"),
        variantKey = rs.getString("variant_key"),
        weight = rs.getInt("weight"),
      )
    }
    return configs.groupBy { it.key }.map { (_, group) ->
      val head = group.first()
      ExperimentResponse(
        experimentId = head.id,
        experimentKey = head.key,
        name = head.name,
        status = head.status,
        salt = head.salt,
        trafficPct = head.trafficPct,
        attributionWindowSeconds = head.attributionWindowSeconds,
        variants = group.map { VariantWeight(it.variantKey, it.weight) },
      )
    }
  }

  @Transactional
  fun create(request: CreateExperimentRequest): ExperimentResponse {
    val keyHolder = GeneratedKeyHolder()
    jdbc.update({ connection ->
      val ps = connection.prepareStatement(
        """
        INSERT INTO experiments (experiment_key, name, status, salt, traffic_pct, attribution_window_seconds)
        VALUES (?, ?, 'draft', ?, ?, ?)
        """.trimIndent(),
        Statement.RETURN_GENERATED_KEYS,
      )
      ps.setString(1, request.experimentKey)
      ps.setString(2, request.name)
      ps.setString(3, request.salt)
      ps.setInt(4, request.trafficPct)
      ps.setInt(5, request.attributionWindowSeconds)
      ps
    }, keyHolder)
    val experimentId = keyHolder.key?.toInt()
      ?: (keyHolder.keys?.get("experiment_id") as? Number)?.toInt()
      ?: error("failed to create experiment")

    request.variants.forEach { variant ->
      jdbc.update(
        "INSERT INTO variants (experiment_id, variant_key, weight) VALUES (?, ?, ?)",
        experimentId, variant.key, variant.weight,
      )
    }
    request.metrics.forEach { metric ->
      jdbc.update(
        """
        INSERT INTO metrics (experiment_id, metric_key, metric_type, role, min_effect, higher_is_better)
        VALUES (?, ?, ?, ?, ?, ?)
        """.trimIndent(),
        experimentId, metric.metricKey, metric.metricType, metric.role, metric.minEffect, metric.higherIsBetter,
      )
    }
    return get(request.experimentKey)!!
  }

  fun get(experimentKey: String): ExperimentResponse? =
    list().find { it.experimentKey == experimentKey }

  fun start(experimentKey: String): Boolean =
    jdbc.update(
      """
      UPDATE experiments
      SET status = 'running', started_at = COALESCE(started_at, ?), stopped_at = NULL
      WHERE experiment_key = ? AND status IN ('draft', 'stopped')
      """.trimIndent(),
      Timestamp.from(Instant.now()),
      experimentKey,
    ) > 0

  fun stop(experimentKey: String): Boolean =
    jdbc.update(
      """
      UPDATE experiments
      SET status = 'stopped', stopped_at = ?
      WHERE experiment_key = ? AND status = 'running'
      """.trimIndent(),
      Timestamp.from(Instant.now()),
      experimentKey,
    ) > 0

  private data class Row(
    val id: Int,
    val key: String,
    val name: String,
    val status: String,
    val salt: String,
    val trafficPct: Int,
    val attributionWindowSeconds: Int,
    val variantKey: String,
    val weight: Int,
  )
}
