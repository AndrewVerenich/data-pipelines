package com.smarthome.simulator.model

import java.time.Instant

data class TemperatureReading(
  val roomId: String,
  val temperature: Double,
  val ts: String = Instant.now().toString(),
)

data class HumidityReading(
  val roomId: String,
  val humidity: Double,
  val ts: String = Instant.now().toString(),
)

data class MotionReading(
  val roomId: String,
  val detected: Boolean,
  val ts: String = Instant.now().toString(),
)

data class DoorWindowReading(
  val roomId: String,
  /** OPEN or CLOSED */
  val state: String,
  val ts: String = Instant.now().toString(),
)

data class LightLevelReading(
  val roomId: String,
  val lux: Double,
  val ts: String = Instant.now().toString(),
)

data class HvacCommandMessage(
  val roomId: String,
  val action: String,
  val reason: String? = null,
  val ts: String = Instant.now().toString(),
)

data class LightingCommandMessage(
  val roomId: String,
  val action: String,
  val reason: String? = null,
  val ts: String = Instant.now().toString(),
)
