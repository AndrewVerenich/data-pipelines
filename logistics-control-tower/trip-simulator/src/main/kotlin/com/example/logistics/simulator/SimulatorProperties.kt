package com.example.logistics.simulator

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties("logistics")
data class SimulatorProperties(
    val gpsTopic: String = "logistics.gps_pings",
    val eventsTopic: String = "logistics.trip_events",
    val activeTrips: Int = 40,
    val tickMs: Long = 5_000,
    val backfillHours: Int = 3,
    val dwellSlaSec: Int = 600,
)
