package com.smarthome.simulator.model

import com.fasterxml.jackson.annotation.JsonProperty
import java.time.Instant

data class TemperatureReading(
  @JsonProperty("room_id") val roomId: String,
  val temperature: Double,
  val ts: String = Instant.now().toString(),
)

data class HumidityReading(
  @JsonProperty("room_id") val roomId: String,
  val humidity: Double,
  val ts: String = Instant.now().toString(),
)

data class MotionReading(
  @JsonProperty("room_id") val roomId: String,
  val detected: Boolean,
  val ts: String = Instant.now().toString(),
)

data class DoorWindowReading(
  @JsonProperty("room_id") val roomId: String,
  /** OPEN or CLOSED */
  val state: String,
  val ts: String = Instant.now().toString(),
)

data class LightLevelReading(
  @JsonProperty("room_id") val roomId: String,
  val lux: Double,
  val ts: String = Instant.now().toString(),
)

data class HvacCommandMessage(
  @JsonProperty("room_id") val roomId: String,
  val action: String,
  val reason: String? = null,
  val ts: String = Instant.now().toString(),
)

data class LightingCommandMessage(
  @JsonProperty("room_id") val roomId: String,
  val action: String,
  val reason: String? = null,
  val ts: String = Instant.now().toString(),
)
