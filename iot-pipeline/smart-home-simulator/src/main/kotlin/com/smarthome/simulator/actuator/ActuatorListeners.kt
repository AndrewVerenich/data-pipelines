package com.smarthome.simulator.actuator

import com.smarthome.simulator.model.HvacCommandMessage
import com.smarthome.simulator.model.LightingCommandMessage
import com.smarthome.simulator.physics.HvacAction
import com.smarthome.simulator.physics.RoomPhysicsEngine
import org.slf4j.LoggerFactory
import org.springframework.kafka.annotation.KafkaListener
import org.springframework.stereotype.Component

@Component
class ActuatorListeners(
  private val physics: RoomPhysicsEngine,
) {
  private val log = LoggerFactory.getLogger(javaClass)

  @KafkaListener(topics = ["command.hvac"], groupId = "smart-home-actuators-hvac")
  fun onHvac(cmd: HvacCommandMessage) {
    val state = physics.get(cmd.roomId) ?: return
    val action = when (cmd.action.uppercase()) {
      "HEAT" -> HvacAction.HEAT
      "COOL" -> HvacAction.COOL
      else -> HvacAction.IDLE
    }
    state.hvacAction.set(action)
    log.info("HVAC {} {} ({})", cmd.roomId, action, cmd.reason)
  }

  @KafkaListener(topics = ["command.lighting"], groupId = "smart-home-actuators-lighting")
  fun onLighting(cmd: LightingCommandMessage) {
    val state = physics.get(cmd.roomId) ?: return
    when (cmd.action.uppercase()) {
      "ON", "LIGHTS_ON" -> state.lightsOn.set(true)
      "OFF", "LIGHTS_OFF" -> state.lightsOn.set(false)
    }
    log.info("Light {} {} ({})", cmd.roomId, cmd.action, cmd.reason)
  }
}
