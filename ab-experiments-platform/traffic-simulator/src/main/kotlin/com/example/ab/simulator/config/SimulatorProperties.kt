package com.example.ab.simulator.config

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties("ab")
data class SimulatorProperties(
  val assignmentUrl: String,
  val liveIntervalMs: Long = 2_000,
)
