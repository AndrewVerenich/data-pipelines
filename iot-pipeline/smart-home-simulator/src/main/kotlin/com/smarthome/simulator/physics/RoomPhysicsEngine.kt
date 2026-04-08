package com.smarthome.simulator.physics

import com.smarthome.simulator.config.SmartHomeProperties
import jakarta.annotation.PostConstruct
import org.springframework.stereotype.Component
import java.time.ZonedDateTime
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ThreadLocalRandom
import kotlin.math.max
import kotlin.random.Random

interface RoomPhysicsEngine {
  fun allStates(): Collection<RoomState>
  fun get(roomId: String): RoomState?
  fun tick()
}

@Component
class DefaultRoomPhysicsEngine(
  private val properties: SmartHomeProperties,
) : RoomPhysicsEngine {
  private val rooms = ConcurrentHashMap<String, RoomState>()

  @PostConstruct
  fun init() {
    properties.roomList().forEach { id ->
      rooms[id] = RoomState(id).also {
        it.temperature.set(19.0 + Random.nextDouble() * 2.0)
        it.humidity.set(40.0 + Random.nextDouble() * 10.0)
      }
    }
  }

  override fun allStates(): Collection<RoomState> = rooms.values

  override fun get(roomId: String): RoomState? = rooms[roomId]

  override fun tick() {
    val ambient = properties.ambientTemperature
    val hour = ZonedDateTime.now().hour
    for (state in rooms.values) {
      var t = state.temperature.get()
      t += (ambient - t) * 0.0012
      when (state.hvacAction.get()) {
        HvacAction.HEAT -> t += 0.048
        HvacAction.COOL -> t -= 0.048
        HvacAction.IDLE -> {}
      }
      t += ThreadLocalRandom.current().nextGaussian() * 0.02
      state.temperature.set(t)

      var h = state.humidity.get()
      h += (50.0 - h) * 0.002 - (t - 21.0) * 0.01
      h += ThreadLocalRandom.current().nextGaussian() * 0.15
      state.humidity.set(h.coerceIn(25.0, 75.0))

      val outdoorLux = outdoorLuxCurve(hour)
      val base = if (state.lightsOn.get()) max(outdoorLux * 0.4, 420.0) else outdoorLux * 0.25
      state.lastLux.set(max(5.0, base + ThreadLocalRandom.current().nextGaussian() * 15))
    }
  }

  private fun outdoorLuxCurve(hour: Int): Double {
    if (hour in 8..18) return 400.0 + (hour - 8) * 25.0
    if (hour in 19..21) return 150.0
    return 35.0
  }
}
