package com.example.ab.assignment.repository

import com.example.ab.assignment.model.AssignmentRow
import org.springframework.dao.DuplicateKeyException
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Repository
import java.sql.Timestamp
import java.time.Instant

@Repository
class AssignmentRepository(private val jdbc: JdbcTemplate) {
  fun find(experimentId: Int, userId: String): AssignmentRow? =
    jdbc.query(
      """
      SELECT experiment_id, experiment_key, user_id, variant_key, bucket, assigned_at
      FROM assignments
      WHERE experiment_id = ? AND user_id = ?
      """.trimIndent(),
      { rs, _ ->
        AssignmentRow(
          experimentId = rs.getInt("experiment_id"),
          experimentKey = rs.getString("experiment_key"),
          userId = rs.getString("user_id"),
          variantKey = rs.getString("variant_key"),
          bucket = rs.getInt("bucket"),
          assignedAt = rs.getTimestamp("assigned_at").toInstant(),
        )
      },
      experimentId,
      userId,
    ).firstOrNull()

  fun insert(row: AssignmentRow): AssignmentRow {
    try {
      jdbc.update(
        """
        INSERT INTO assignments (experiment_id, experiment_key, user_id, variant_key, bucket, assigned_at)
        VALUES (?, ?, ?, ?, ?, ?)
        """.trimIndent(),
        row.experimentId,
        row.experimentKey,
        row.userId,
        row.variantKey,
        row.bucket,
        Timestamp.from(row.assignedAt),
      )
      return row
    } catch (_: DuplicateKeyException) {
      return find(row.experimentId, row.userId)
        ?: error("assignment race for ${row.experimentKey}/${row.userId}")
    }
  }

  fun countBackfillLike(): Long =
    jdbc.queryForObject(
      "SELECT count(*) FROM assignments WHERE user_id LIKE '%:u-%'",
      Long::class.java,
    ) ?: 0L

  fun insertBatch(rows: List<AssignmentRow>) {
    if (rows.isEmpty()) return
    jdbc.batchUpdate(
      """
      INSERT INTO assignments (experiment_id, experiment_key, user_id, variant_key, bucket, assigned_at)
      VALUES (?, ?, ?, ?, ?, ?)
      ON CONFLICT (experiment_id, user_id) DO NOTHING
      """.trimIndent(),
      rows,
      500,
    ) { ps, row ->
      ps.setInt(1, row.experimentId)
      ps.setString(2, row.experimentKey)
      ps.setString(3, row.userId)
      ps.setString(4, row.variantKey)
      ps.setInt(5, row.bucket)
      ps.setTimestamp(6, Timestamp.from(row.assignedAt))
    }
  }
}
