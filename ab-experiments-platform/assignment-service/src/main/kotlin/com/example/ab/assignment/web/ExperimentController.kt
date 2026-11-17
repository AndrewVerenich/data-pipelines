package com.example.ab.assignment.web

import com.example.ab.assignment.model.CreateExperimentRequest
import com.example.ab.assignment.model.ExperimentResponse
import com.example.ab.assignment.service.ExperimentService
import jakarta.validation.Valid
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/experiments")
class ExperimentController(private val experiments: ExperimentService) {
  @GetMapping
  fun list(): List<ExperimentResponse> = experiments.list()

  @GetMapping("/{experimentKey}")
  fun get(@PathVariable experimentKey: String): ExperimentResponse = experiments.get(experimentKey)

  @PostMapping
  fun create(@Valid @RequestBody request: CreateExperimentRequest): ExperimentResponse =
    experiments.create(request)

  @PostMapping("/{experimentKey}/start")
  fun start(@PathVariable experimentKey: String): ExperimentResponse = experiments.start(experimentKey)

  @PostMapping("/{experimentKey}/stop")
  fun stop(@PathVariable experimentKey: String): ExperimentResponse = experiments.stop(experimentKey)
}
