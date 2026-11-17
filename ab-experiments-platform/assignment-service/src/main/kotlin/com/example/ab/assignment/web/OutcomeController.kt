package com.example.ab.assignment.web

import com.example.ab.assignment.model.OutcomeBatchRequest
import com.example.ab.assignment.model.OutcomeRequest
import com.example.ab.assignment.service.OutcomeService
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/outcomes")
class OutcomeController(private val outcomes: OutcomeService) {
  @PostMapping
  @ResponseStatus(HttpStatus.ACCEPTED)
  fun record(@Valid @RequestBody request: OutcomeRequest) {
    outcomes.record(request)
  }

  @PostMapping("/batch")
  @ResponseStatus(HttpStatus.ACCEPTED)
  fun recordBatch(@Valid @RequestBody request: OutcomeBatchRequest) {
    outcomes.recordBatch(request.events)
  }
}
