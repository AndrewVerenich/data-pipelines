package com.example.ab.assignment

import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Repository
import java.util.concurrent.atomic.AtomicReference
import jakarta.annotation.PostConstruct

data class VariantWeight(val key: String, val weight: Int)

data class ExperimentConfig(
    val id: Int,
    val key: String,
    val salt: String,
    val trafficPct: Int,
    val variants: List<VariantWeight>,
)

@Repository
class ExperimentRepository(private val jdbc: JdbcTemplate) {
    private val cache = AtomicReference<Map<String, ExperimentConfig>>(emptyMap())

    @PostConstruct
    fun load() {
        refresh()
    }

    @Scheduled(fixedDelay = 30_000)
    fun refresh() {
        val rows = jdbc.query(
            """
            SELECT e.experiment_id, e.experiment_key, e.salt, e.traffic_pct, v.variant_key, v.weight
            FROM experiments e
            JOIN variants v ON v.experiment_id = e.experiment_id
            WHERE e.status = 'running'
            ORDER BY e.experiment_id, v.variant_key
            """.trimIndent()
        ) { rs, _ ->
            Row(
                id = rs.getInt("experiment_id"),
                key = rs.getString("experiment_key"),
                salt = rs.getString("salt"),
                trafficPct = rs.getInt("traffic_pct"),
                variantKey = rs.getString("variant_key"),
                weight = rs.getInt("weight"),
            )
        }
        cache.set(
            rows.groupBy { it.key }.mapValues { (_, group) ->
                val head = group.first()
                ExperimentConfig(
                    id = head.id,
                    key = head.key,
                    salt = head.salt,
                    trafficPct = head.trafficPct,
                    variants = group.map { VariantWeight(it.variantKey, it.weight) },
                )
            }
        )
    }

    fun find(experimentKey: String): ExperimentConfig? = cache.get()[experimentKey]

    private data class Row(
        val id: Int,
        val key: String,
        val salt: String,
        val trafficPct: Int,
        val variantKey: String,
        val weight: Int,
    )
}
