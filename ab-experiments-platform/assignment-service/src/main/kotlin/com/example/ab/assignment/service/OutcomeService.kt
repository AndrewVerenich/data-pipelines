package com.example.ab.assignment.service

import com.example.ab.assignment.model.OutcomeRequest
import com.example.ab.assignment.repository.OutcomeRepository
import com.example.ab.assignment.repository.OutcomeRow
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.web.server.ResponseStatusException
import java.time.Instant
import java.util.UUID

@Service
class OutcomeService(
  private val assignmentService: AssignmentService,
  private val outcomes: OutcomeRepository,
) {
  private val allowedTypes = setOf("conversion", "revenue", "refund")

  fun record(request: OutcomeRequest) {
    outcomes.insert(toRow(request))
  }

  fun recordBatch(requests: List<OutcomeRequest>) {
    outcomes.insertBatch(requests.map(::toRow))
  }

  private fun toRow(request: OutcomeRequest): OutcomeRow {
    if (request.eventType !in allowedTypes) {
      throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Unsupported event_type: ${request.eventType}")
    }
    val experiment = assignmentService.requireRunning(request.experimentKey)
    val eventTime = request.eventTime?.let(Instant::parse) ?: Instant.now()
    return OutcomeRow(
      eventId = request.eventId ?: UUID.randomUUID().toString(),
      experimentId = experiment.id,
      experimentKey = experiment.key,
      userId = request.userId,
      eventType = request.eventType,
      value = request.value,
      eventTime = eventTime,
    )
  }
}
