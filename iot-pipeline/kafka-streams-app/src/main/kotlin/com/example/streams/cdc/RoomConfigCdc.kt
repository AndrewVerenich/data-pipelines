package com.example.streams.cdc

import com.example.streams.model.RoomConfig
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper

object RoomConfigCdc {
  private val mapper = ObjectMapper().findAndRegisterModules()

  fun parse(raw: String): RoomConfig? {
    return try {
      val root: JsonNode = mapper.readTree(raw)
      val payload = root.get("payload") ?: return null
      val op = payload.get("op")?.asText() ?: return null
      if (op == "d") return null
      val after = payload.get("after") ?: return null
      RoomConfig(
        roomId = after.get("room_id").asText(),
        desiredTemperature = after.get("desired_temperature").asDouble(),
        climateDeadband = after.get("climate_deadband")?.asDouble() ?: 1.0,
        hvacMode = after.get("hvac_mode").asText(),
        securityMode = after.get("security_mode").asText(),
        lightingMode = after.get("lighting_mode").asText(),
        luxOnThreshold = after.get("lux_on_threshold")?.asDouble() ?: 200.0,
        luxOffThreshold = after.get("lux_off_threshold")?.asDouble() ?: 350.0,
      )
    } catch (_: Exception) {
      null
    }
  }
}
