package com.example.retl.email

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties("email-mock")
data class EmailMockProperties(
    val rateLimitProbability: Double = 0.08,
)
