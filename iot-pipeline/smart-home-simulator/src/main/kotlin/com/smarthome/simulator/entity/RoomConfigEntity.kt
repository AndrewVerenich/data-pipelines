package com.smarthome.simulator.entity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.Instant

@Entity
@Table(name = "room_config")
open class RoomConfigEntity(
  @Id
  @Column(name = "room_id", length = 64)
  var roomId: String = "",

  @Column(name = "desired_temperature", nullable = false)
  var desiredTemperature: Double = 22.0,

  @Column(name = "climate_deadband", nullable = false)
  var climateDeadband: Double = 1.0,

  @Column(name = "hvac_mode", length = 16, nullable = false)
  var hvacMode: String = "auto",

  @Column(name = "security_mode", length = 16, nullable = false)
  var securityMode: String = "disarmed",

  @Column(name = "lighting_mode", length = 16, nullable = false)
  var lightingMode: String = "auto",

  @Column(name = "lux_on_threshold", nullable = false)
  var luxOnThreshold: Double = 200.0,

  @Column(name = "lux_off_threshold", nullable = false)
  var luxOffThreshold: Double = 350.0,

  @Column(name = "updated_at", nullable = false)
  var updatedAt: Instant = Instant.now(),
)
