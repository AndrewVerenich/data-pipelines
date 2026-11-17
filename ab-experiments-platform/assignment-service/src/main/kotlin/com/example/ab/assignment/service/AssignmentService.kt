package com.example.ab.assignment.service

import com.example.ab.assignment.model.AssignResponse
import com.example.ab.assignment.model.AssignmentRow
import com.example.ab.assignment.model.ExperimentConfig
import com.example.ab.assignment.repository.AssignmentRepository
import com.example.ab.assignment.repository.ExperimentRepository
import com.example.ab.assignment.bucketing.Bucketer
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.web.server.ResponseStatusException
import java.time.Instant

@Service
class AssignmentService(
  private val experiments: ExperimentRepository,
  private val assignments: AssignmentRepository,
) {
  fun assign(experimentKey: String, userId: String): AssignResponse {
    val experiment = experiments.findConfig(experimentKey)
      ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "Unknown experiment: $experimentKey")
    if (experiment.status != "running") {
      throw ResponseStatusException(HttpStatus.CONFLICT, "Experiment is not running: ${experiment.status}")
    }

    val existing = assignments.find(experiment.id, userId)
    if (existing != null) return existing.toResponse(inExperiment = true)

    val bucket = Bucketer.bucket(experiment.salt, userId)
    val variant = Bucketer.variant(
      bucket,
      experiment.trafficPct,
      experiment.variants.map { it.key to it.weight },
    )
    if (variant == null) {
      return AssignResponse(experiment.key, experiment.id, userId, false, null, bucket, null)
    }

    val saved = assignments.insert(
      AssignmentRow(
        experimentId = experiment.id,
        experimentKey = experiment.key,
        userId = userId,
        variantKey = variant,
        bucket = bucket,
        assignedAt = Instant.now(),
      )
    )
    return saved.toResponse(inExperiment = true)
  }

  fun requireRunning(experimentKey: String): ExperimentConfig {
    val experiment = experiments.findConfig(experimentKey)
      ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "Unknown experiment: $experimentKey")
    if (experiment.status != "running") {
      throw ResponseStatusException(HttpStatus.CONFLICT, "Experiment is not running: ${experiment.status}")
    }
    return experiment
  }

  private fun AssignmentRow.toResponse(inExperiment: Boolean) = AssignResponse(
    experimentKey = experimentKey,
    experimentId = experimentId,
    userId = userId,
    inExperiment = inExperiment,
    variantKey = variantKey,
    bucket = bucket,
    assignedAt = assignedAt.toString(),
  )
}
