package com.example.ab.assignment.service

import com.example.ab.assignment.model.AssignResponse
import com.example.ab.assignment.model.ExperimentConfig
import com.example.ab.assignment.model.ExposureEvent
import com.example.ab.assignment.repository.ExperimentRepository
import com.example.ab.bucketing.Bucketer
import com.example.ab.bucketing.ExposureVersion
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.web.server.ResponseStatusException
import java.time.Instant
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

@Service
class AssignmentService(
  private val experiments: ExperimentRepository,
  private val publisher: ExposurePublisher,
) {
  private val seen = ConcurrentHashMap<String, AssignResponse>()

  fun assign(experimentKey: String, userId: String): AssignResponse {
    val experiment = experiments.find(experimentKey)
      ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "Unknown experiment: $experimentKey")
    val cacheKey = "${experiment.key}:${experiment.salt}:$userId"
    return seen.computeIfAbsent(cacheKey) { compute(experiment, userId) }
  }

  private fun compute(experiment: ExperimentConfig, userId: String): AssignResponse {
    val bucket = Bucketer.bucket(experiment.salt, userId)
    val variant = Bucketer.variant(
      bucket,
      experiment.trafficPct,
      experiment.variants.map { it.key to it.weight },
    )
    if (variant == null) {
      return AssignResponse(experiment.key, experiment.id, userId, false, null, bucket, null)
    }
    val exposedAtMillis = Instant.now().toEpochMilli()
    val response = AssignResponse(
      experimentKey = experiment.key,
      experimentId = experiment.id,
      userId = userId,
      inExperiment = true,
      variantKey = variant,
      bucket = bucket,
      exposedAt = exposedAtMillis / 1000.0,
    )
    publisher.publish(
      ExposureEvent(
        exposureId = UUID.randomUUID().toString(),
        experimentId = experiment.id,
        experimentKey = experiment.key,
        variantKey = variant,
        userId = userId,
        bucket = bucket,
        exposedAt = response.exposedAt!!,
        exposureVersion = ExposureVersion.of(exposedAtMillis),
      )
    )
    return response
  }
}
