package com.example.ab.assignment.config

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties("ab")
data class AssignmentProperties(
  val exposuresTopic: String = "ab.exposures",
)
