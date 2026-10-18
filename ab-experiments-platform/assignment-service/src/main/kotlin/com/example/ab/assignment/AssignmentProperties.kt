package com.example.ab.assignment

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties("ab")
data class AssignmentProperties(
    val exposuresTopic: String = "ab.exposures",
)
