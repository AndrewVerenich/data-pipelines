package com.example.ab.simulator

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties("ab")
data class SimulatorProperties(
    val assignmentUrl: String,
    val clickhouseUrl: String,
    val clickhouseUser: String,
    val clickhousePassword: String,
    val outcomesTopic: String = "ab.outcomes",
    val liveIntervalMs: Long = 2_000,
)
