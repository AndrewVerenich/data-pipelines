package com.example.streams.enums

import com.fasterxml.jackson.annotation.JsonCreator
import com.fasterxml.jackson.annotation.JsonValue

enum class HvacMode(val wire: String) {
  AUTO("auto"),
  HEAT("heat"),
  COOL("cool"),
  OFF("off"),
  ;

  @JsonValue
  fun toWire(): String = wire

  companion object {
    @JvmStatic
    @JsonCreator
    fun fromWire(value: String): HvacMode =
      entries.firstOrNull { it.wire.equals(value, ignoreCase = true) }
        ?: throw IllegalArgumentException("Unknown HvacMode: $value")
  }
}

enum class SecurityMode(val wire: String) {
  ARMED("armed"),
  DISARMED("disarmed"),
  NIGHT("night"),
  ;

  @JsonValue
  fun toWire(): String = wire

  companion object {
    @JvmStatic
    @JsonCreator
    fun fromWire(value: String): SecurityMode =
      entries.firstOrNull { it.wire.equals(value, ignoreCase = true) }
        ?: throw IllegalArgumentException("Unknown SecurityMode: $value")
  }
}

enum class LightingMode(val wire: String) {
  AUTO("auto"),
  MANUAL("manual"),
  OFF("off"),
  ;

  @JsonValue
  fun toWire(): String = wire

  companion object {
    @JvmStatic
    @JsonCreator
    fun fromWire(value: String): LightingMode =
      entries.firstOrNull { it.wire.equals(value, ignoreCase = true) }
        ?: throw IllegalArgumentException("Unknown LightingMode: $value")
  }
}

enum class StreamOutputAction(val wire: String) {
  HEAT("HEAT"),
  COOL("COOL"),
  IDLE("IDLE"),
  LIGHTS_ON("LIGHTS_ON"),
  LIGHTS_OFF("LIGHTS_OFF"),
  ;

  @JsonValue
  fun toWire(): String = wire

  companion object {
    @JvmStatic
    @JsonCreator
    fun fromWire(value: String): StreamOutputAction =
      entries.firstOrNull { it.wire.equals(value, ignoreCase = true) }
        ?: throw IllegalArgumentException("Unknown StreamOutputAction: $value")
  }
}

enum class DoorWindowState(val wire: String) {
  OPEN("OPEN"),
  CLOSED("CLOSED"),
  ;

  @JsonValue
  fun toWire(): String = wire

  companion object {
    @JvmStatic
    @JsonCreator
    fun fromWire(value: String): DoorWindowState =
      entries.firstOrNull { it.wire.equals(value, ignoreCase = true) }
        ?: throw IllegalArgumentException("Unknown DoorWindowState: $value")
  }
}

enum class SecurityAlertType(val wire: String) {
  INTRUSION("INTRUSION"),
  MOTION_WHILE_ARMED("MOTION_WHILE_ARMED"),
  ;

  @JsonValue
  fun toWire(): String = wire

  companion object {
    @JvmStatic
    @JsonCreator
    fun fromWire(value: String): SecurityAlertType =
      entries.firstOrNull { it.wire.equals(value, ignoreCase = true) }
        ?: throw IllegalArgumentException("Unknown SecurityAlertType: $value")
  }
}

enum class AlertSeverity(val wire: String) {
  HIGH("HIGH"),
  MEDIUM("MEDIUM"),
  LOW("LOW"),
  ;

  @JsonValue
  fun toWire(): String = wire

  companion object {
    @JvmStatic
    @JsonCreator
    fun fromWire(value: String): AlertSeverity =
      entries.firstOrNull { it.wire.equals(value, ignoreCase = true) }
        ?: throw IllegalArgumentException("Unknown AlertSeverity: $value")
  }
}
