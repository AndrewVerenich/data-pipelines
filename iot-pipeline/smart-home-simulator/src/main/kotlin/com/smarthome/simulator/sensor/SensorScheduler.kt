package com.smarthome.simulator.sensor

import com.smarthome.simulator.model.DoorWindowReading
import com.smarthome.simulator.model.HumidityReading
import com.smarthome.simulator.model.LightLevelReading
import com.smarthome.simulator.model.MotionReading
import com.smarthome.simulator.model.TemperatureReading
import com.smarthome.simulator.physics.RoomPhysicsEngine
import org.springframework.kafka.core.KafkaTemplate
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import java.time.ZonedDateTime
import kotlin.random.Random

@Component
class SensorScheduler(
  private val kafka: KafkaTemplate<String, Any>,
  private val physics: RoomPhysicsEngine,
) {
  @Scheduled(fixedRate = 1000)
  fun physicsTick() {
    physics.tick()
  }

  @Scheduled(fixedRate = 1000)
  fun publishTemperature() {
    for (state in physics.allStates()) {
      val msg = TemperatureReading(state.roomId, state.temperature.get())
      kafka.send("sensor.temperature", state.roomId, msg)
    }
  }

  @Scheduled(fixedRate = 2000)
  fun publishHumidity() {
    for (state in physics.allStates()) {
      val msg = HumidityReading(state.roomId, state.humidity.get())
      kafka.send("sensor.humidity", state.roomId, msg)
    }
  }

  @Scheduled(fixedRate = 2000)
  fun publishLux() {
    for (state in physics.allStates()) {
      val msg = LightLevelReading(state.roomId, state.lastLux.get())
      kafka.send("sensor.light-level", state.roomId, msg)
    }
  }

  @Scheduled(fixedRate = 1500)
  fun publishMotion() {
    val hour = ZonedDateTime.now().hour
    val pMotion = when (hour) {
      in 7..22 -> 0.35
      else -> 0.08
    }
    for (state in physics.allStates()) {
      val detected = Random.nextDouble() < pMotion
      kafka.send("sensor.motion", state.roomId, MotionReading(state.roomId, detected))
    }
  }

  @Scheduled(fixedRate = 4000)
  fun publishDoor() {
    for (state in physics.allStates()) {
      if (Random.nextDouble() < 0.12) {
        val open = Random.nextBoolean()
        state.doorOpen.set(open)
        kafka.send(
          "sensor.door-window",
          state.roomId,
          DoorWindowReading(state.roomId, if (open) "OPEN" else "CLOSED"),
        )
      }
    }
  }
}
