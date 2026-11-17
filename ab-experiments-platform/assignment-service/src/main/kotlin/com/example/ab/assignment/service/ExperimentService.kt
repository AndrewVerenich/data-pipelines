package com.example.ab.assignment.service

import com.example.ab.assignment.model.CreateExperimentRequest
import com.example.ab.assignment.model.ExperimentResponse
import com.example.ab.assignment.repository.ExperimentRepository
import org.springframework.dao.DuplicateKeyException
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.web.server.ResponseStatusException

@Service
class ExperimentService(private val experiments: ExperimentRepository) {
  fun list(): List<ExperimentResponse> = experiments.list()

  fun get(experimentKey: String): ExperimentResponse =
    experiments.get(experimentKey)
      ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "Unknown experiment: $experimentKey")

  fun create(request: CreateExperimentRequest): ExperimentResponse {
    validate(request)
    return try {
      experiments.create(request)
    } catch (_: DuplicateKeyException) {
      throw ResponseStatusException(HttpStatus.CONFLICT, "Experiment already exists: ${request.experimentKey}")
    }
  }

  fun start(experimentKey: String): ExperimentResponse {
    if (!experiments.start(experimentKey)) {
      get(experimentKey)
      throw ResponseStatusException(HttpStatus.CONFLICT, "Cannot start experiment in current status")
    }
    return get(experimentKey)
  }

  fun stop(experimentKey: String): ExperimentResponse {
    if (!experiments.stop(experimentKey)) {
      get(experimentKey)
      throw ResponseStatusException(HttpStatus.CONFLICT, "Cannot stop experiment in current status")
    }
    return get(experimentKey)
  }

  private fun validate(request: CreateExperimentRequest) {
    if (request.variants.sumOf { it.weight } <= 0) {
      throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Variant weights must be positive")
    }
    val roles = request.metrics.map { it.role }
    if (roles.count { it == "primary" } != 1) {
      throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Exactly one primary metric is required")
    }
  }
}
