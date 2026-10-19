package com.example.retl.simulator

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties("retl")
data class SimulatorProperties(
    val eventsTopic: String = "commerce.events",
    val activeUsers: Int = 80,
    val tickMs: Long = 4_000,
    val backfillHours: Int = 6,
    val abandonWindowHours: Int = 24,
    val graceMinutes: Int = 30,
)
