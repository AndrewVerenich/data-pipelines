package com.example.ab.assignment.repository

import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Repository
import java.sql.Timestamp
import java.time.Instant

data class OutcomeRow(
  val eventId: String,
  val experimentId: Int,
  val experimentKey: String,
  val userId: String,
  val eventType: String,
  val value: Double,
  val eventTime: Instant,
)

@Repository
class OutcomeRepository(private val jdbc: JdbcTemplate) {
  fun insert(row: OutcomeRow) {
    jdbc.update(
      """
      INSERT INTO outcomes (event_id, experiment_id, experiment_key, user_id, event_type, value, event_time)
      VALUES (?, ?, ?, ?, ?, ?, ?)
      ON CONFLICT (event_id) DO NOTHING
      """.trimIndent(),
      row.eventId,
      row.experimentId,
      row.experimentKey,
      row.userId,
      row.eventType,
      row.value,
      Timestamp.from(row.eventTime),
    )
  }

  fun insertBatch(rows: List<OutcomeRow>) {
    if (rows.isEmpty()) return
    jdbc.batchUpdate(
      """
      INSERT INTO outcomes (event_id, experiment_id, experiment_key, user_id, event_type, value, event_time)
      VALUES (?, ?, ?, ?, ?, ?, ?)
      ON CONFLICT (event_id) DO NOTHING
      """.trimIndent(),
      rows,
      500,
    ) { ps, row ->
      ps.setString(1, row.eventId)
      ps.setInt(2, row.experimentId)
      ps.setString(3, row.experimentKey)
      ps.setString(4, row.userId)
      ps.setString(5, row.eventType)
      ps.setDouble(6, row.value)
      ps.setTimestamp(7, Timestamp.from(row.eventTime))
    }
  }
}
