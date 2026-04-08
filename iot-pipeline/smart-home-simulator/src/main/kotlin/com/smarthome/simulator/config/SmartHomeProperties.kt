package com.smarthome.simulator.config

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "smarthome")
data class SmartHomeProperties(
  var rooms: String = "living-room,bedroom,kitchen",
  var ambientTemperature: Double = 12.0,
) {
  fun roomList(): List<String> = rooms.split(',').map { it.trim() }.filter { it.isNotEmpty() }
}
