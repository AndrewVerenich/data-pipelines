package com.example.ab.assignment.web

import com.example.ab.assignment.model.AssignResponse
import com.example.ab.assignment.service.AssignmentService
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@RestController
class AssignController(private val assignmentService: AssignmentService) {
  @GetMapping("/api/assign")
  fun assignGet(
    @RequestParam("experiment_key") experimentKey: String,
    @RequestParam("user_id") userId: String,
  ): AssignResponse = assignmentService.assign(experimentKey, userId)

  @PostMapping("/api/assign")
  fun assignPost(
    @RequestParam("experiment_key") experimentKey: String,
    @RequestParam("user_id") userId: String,
  ): AssignResponse = assignmentService.assign(experimentKey, userId)
}
