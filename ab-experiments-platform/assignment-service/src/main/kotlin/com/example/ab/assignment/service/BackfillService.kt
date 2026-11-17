package com.example.ab.assignment.service

import com.example.ab.assignment.model.AssignmentRow
import com.example.ab.assignment.repository.AssignmentRepository
import com.example.ab.assignment.repository.OutcomeRepository
import com.example.ab.assignment.repository.OutcomeRow
import com.example.ab.assignment.demo.DemoCatalog
import com.example.ab.assignment.demo.OutcomeSampler
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.UUID

@Service
class BackfillService(
  private val assignmentService: AssignmentService,
  private val assignments: AssignmentRepository,
  private val outcomes: OutcomeRepository,
) {
  private val log = LoggerFactory.getLogger(BackfillService::class.java)

  fun ensureDemoBackfill() {
    val existing = assignments.countBackfillLike()
    if (existing > 0L) {
      log.info("backfill already present ({} assignments), skipping", existing)
      return
    }
    val start = Instant.now().minus(14, ChronoUnit.DAYS)
    val spanMillis = 14L * 24 * 60 * 60 * 1000
    DemoCatalog.experiments.forEach { effect ->
      val experiment = assignmentService.requireRunning(effect.experimentKey)
      val assignmentRows = mutableListOf<AssignmentRow>()
      val outcomeRows = mutableListOf<OutcomeRow>()
      repeat(DemoCatalog.BACKFILL_USERS) { index ->
        val user = OutcomeSampler.backfillUser(effect, index)
        val assignedAt = start.plusMillis(index * spanMillis / DemoCatalog.BACKFILL_USERS)
        assignmentRows += AssignmentRow(
          experimentId = experiment.id,
          experimentKey = experiment.key,
          userId = user.userId,
          variantKey = user.variantKey,
          bucket = user.bucket,
          assignedAt = assignedAt,
        )
        if (user.preRevenue > 0.0) {
          outcomeRows += outcome(experiment.id, experiment.key, user.userId, "revenue", user.preRevenue, assignedAt.minus(3, ChronoUnit.DAYS))
        }
        if (user.converted) {
          val at = assignedAt.plusSeconds(45)
          outcomeRows += outcome(experiment.id, experiment.key, user.userId, "conversion", 1.0, at)
          outcomeRows += outcome(experiment.id, experiment.key, user.userId, "revenue", user.revenue, at)
        }
        if (user.refunded) {
          outcomeRows += outcome(experiment.id, experiment.key, user.userId, "refund", 1.0, assignedAt.plusSeconds(50))
        }
      }
      assignments.insertBatch(assignmentRows)
      outcomes.insertBatch(outcomeRows)
      log.info("backfilled {} users for {}", DemoCatalog.BACKFILL_USERS, effect.experimentKey)
    }
  }

  private fun outcome(
    experimentId: Int,
    experimentKey: String,
    userId: String,
    type: String,
    value: Double,
    at: Instant,
  ) = OutcomeRow(
    eventId = UUID.randomUUID().toString(),
    experimentId = experimentId,
    experimentKey = experimentKey,
    userId = userId,
    eventType = type,
    value = value,
    eventTime = at,
  )
}
