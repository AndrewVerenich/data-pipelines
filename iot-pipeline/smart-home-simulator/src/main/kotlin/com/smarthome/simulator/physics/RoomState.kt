package com.smarthome.simulator.physics

import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

class RoomState(
  val roomId: String,
) {
  val temperature = AtomicReference(20.0)
  val humidity = AtomicReference(45.0)
  val hvacAction = AtomicReference(HvacAction.IDLE)
  val lightsOn = AtomicBoolean(false)
  val lastLux = AtomicReference(120.0)
  val doorOpen = AtomicBoolean(false)
}
