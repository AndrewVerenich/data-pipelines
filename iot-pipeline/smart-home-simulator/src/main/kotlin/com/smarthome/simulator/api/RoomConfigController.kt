package com.smarthome.simulator.api

import com.smarthome.simulator.entity.RoomConfigEntity
import com.smarthome.simulator.repo.RoomConfigRepository
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.time.Instant

@RestController
@RequestMapping("/api/rooms")
class RoomConfigController(
  private val repo: RoomConfigRepository,
) {

  @GetMapping
  fun list(): List<RoomConfigResponse> = repo.findAll().map { it.toResponse() }

  @GetMapping("/{roomId}")
  fun one(@PathVariable roomId: String): ResponseEntity<RoomConfigResponse> {
    val e = repo.findById(roomId).orElse(null) ?: return ResponseEntity.notFound().build()
    return ResponseEntity.ok(e.toResponse())
  }

  @PatchMapping("/{roomId}")
  fun patch(@PathVariable roomId: String, @RequestBody body: PatchRoomRequest): ResponseEntity<RoomConfigResponse> {
    val e = repo.findById(roomId).orElse(null) ?: return ResponseEntity.notFound().build()
    body.desiredTemperature?.let { e.desiredTemperature = it }
    body.climateDeadband?.let {
      require(it > 0.1) { "climateDeadband must be > 0.1" }
      e.climateDeadband = it
    }
    body.hvacMode?.let {
      require(it in setOf("auto", "heat", "cool", "off")) { "invalid hvacMode" }
      e.hvacMode = it
    }
    body.securityMode?.let {
      require(it in setOf("armed", "disarmed", "night")) { "invalid securityMode" }
      e.securityMode = it
    }
    body.lightingMode?.let {
      require(it in setOf("auto", "manual", "off")) { "invalid lightingMode" }
      e.lightingMode = it
    }
    body.luxOnThreshold?.let { e.luxOnThreshold = it }
    body.luxOffThreshold?.let { e.luxOffThreshold = it }
    e.updatedAt = Instant.now()
    return ResponseEntity.ok(repo.save(e).toResponse())
  }

  @PutMapping("/{roomId}")
  fun put(@PathVariable roomId: String, @RequestBody body: PatchRoomRequest): ResponseEntity<RoomConfigResponse> {
    val e = repo.findById(roomId).orElse(null) ?: return ResponseEntity.notFound().build()
    body.desiredTemperature?.let { e.desiredTemperature = it }
    body.climateDeadband?.let { e.climateDeadband = it }
    body.hvacMode?.let { e.hvacMode = it }
    body.securityMode?.let { e.securityMode = it }
    body.lightingMode?.let { e.lightingMode = it }
    body.luxOnThreshold?.let { e.luxOnThreshold = it }
    body.luxOffThreshold?.let { e.luxOffThreshold = it }
    e.updatedAt = Instant.now()
    return ResponseEntity.ok(repo.save(e).toResponse())
  }

  private fun RoomConfigEntity.toResponse() = RoomConfigResponse(
    roomId = roomId,
    desiredTemperature = desiredTemperature,
    climateDeadband = climateDeadband,
    hvacMode = hvacMode,
    securityMode = securityMode,
    lightingMode = lightingMode,
    luxOnThreshold = luxOnThreshold,
    luxOffThreshold = luxOffThreshold,
    updatedAt = updatedAt.toString(),
  )
}

data class RoomConfigResponse(
  val roomId: String,
  val desiredTemperature: Double,
  val climateDeadband: Double,
  val hvacMode: String,
  val securityMode: String,
  val lightingMode: String,
  val luxOnThreshold: Double,
  val luxOffThreshold: Double,
  val updatedAt: String,
)

data class PatchRoomRequest(
  val desiredTemperature: Double? = null,
  val climateDeadband: Double? = null,
  val hvacMode: String? = null,
  val securityMode: String? = null,
  val lightingMode: String? = null,
  val luxOnThreshold: Double? = null,
  val luxOffThreshold: Double? = null,
)

