package com.example.streams.model

import com.fasterxml.jackson.annotation.JsonIgnoreProperties

@JsonIgnoreProperties(ignoreUnknown = true)
data class TemperatureReading(
  val roomId: String,
  val temperature: Double,
  val ts: String? = null,
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class MotionReading(
  val roomId: String,
  val detected: Boolean,
  val ts: String? = null,
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class DoorWindowReading(
  val roomId: String,
  val state: String,
  val ts: String? = null,
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class LightLevelReading(
  val roomId: String,
  val lux: Double,
  val ts: String? = null,
)

data class RoomConfig(
  val roomId: String,
  val desiredTemperature: Double,
  val climateDeadband: Double,
  val hvacMode: String,
  val securityMode: String,
  val lightingMode: String,
  val luxOnThreshold: Double,
  val luxOffThreshold: Double,
)

data class AvgAgg(var sum: Double = 0.0, var count: Long = 0) {
  fun add(v: Double) {
    sum += v
    count++
  }

  fun avg(): Double = if (count == 0L) 0.0 else sum / count
}

data class MotionLuxJoin(val motion: MotionReading, val lux: LightLevelReading)

data class LightingOnCtx(val motion: MotionReading, val lux: LightLevelReading, val cfg: RoomConfig)

data class LuxAndConfig(val lux: LightLevelReading, val cfg: RoomConfig)

data class DoorAndConfig(val door: DoorWindowReading, val cfg: RoomConfig)

data class MotionAndConfig(val motion: MotionReading, val cfg: RoomConfig)
