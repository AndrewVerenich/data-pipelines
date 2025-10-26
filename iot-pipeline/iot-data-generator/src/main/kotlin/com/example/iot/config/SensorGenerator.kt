package com.example.iot.config

import com.example.iot.entity.SensorReading
import com.example.iot.repository.SensorRepository
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import java.time.Instant
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.random.Random

@Component
class SensorGenerator(private val repo: SensorRepository) {
  private val logger = LoggerFactory.getLogger(SensorGenerator::class.java)
  private val devices = listOf("sensor-001", "sensor-002")

  init {
    Executors.newSingleThreadScheduledExecutor().scheduleAtFixedRate({
      devices.forEach { id ->
        val reading = SensorReading(
          deviceId = id,
          temperature = 20.0 + Random.nextDouble() * 10.0,
          humidity = 30.0 + Random.nextDouble() * 30.0,
          pressure = 1000.0 + Random.nextDouble() * 20.0,
          timestamp = Instant.now()
        )
        repo.save(reading).also { logger.info("Saved data: $reading") }
      }
    }, 0, 1, TimeUnit.SECONDS)
  }
}