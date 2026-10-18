package com.example.ab.assignment

import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@RestController
class AssignController(private val assignmentService: AssignmentService) {
    @GetMapping("/api/assign")
    fun assign(
        @RequestParam("experiment_key") experimentKey: String,
        @RequestParam("user_id") userId: String,
    ): AssignResponse = assignmentService.assign(experimentKey, userId)
}
