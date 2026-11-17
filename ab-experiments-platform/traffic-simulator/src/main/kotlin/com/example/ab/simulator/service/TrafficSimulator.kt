package com.example.ab.simulator.service

import com.example.ab.simulator.config.SimulatorProperties
import com.example.ab.simulator.demo.DemoCatalog
import com.example.ab.simulator.demo.OutcomeSampler
import com.example.ab.simulator.demo.SampledUser
import com.example.ab.simulator.model.AssignResponse
import com.example.ab.simulator.model.OutcomeEvent
import org.slf4j.LoggerFactory
import org.springframework.boot.ApplicationArguments
import org.springframework.boot.ApplicationRunner
import org.springframework.boot.web.client.RestTemplateBuilder
import org.springframework.http.HttpEntity
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import org.springframework.web.client.getForObject
import org.springframework.web.client.postForEntity
import java.time.Duration
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.concurrent.atomic.AtomicLong

@Component
class TrafficSimulator(
  private val properties: SimulatorProperties,
  restTemplateBuilder: RestTemplateBuilder,
) : ApplicationRunner {
  private val log = LoggerFactory.getLogger(TrafficSimulator::class.java)
  private val restTemplate = restTemplateBuilder
    .setConnectTimeout(Duration.ofSeconds(3))
    .setReadTimeout(Duration.ofMinutes(5))
    .build()
  private val liveSeq = AtomicLong(1)
  @Volatile
  private var ready = false

  override fun run(args: ApplicationArguments) {
    waitForAssignment()
    restTemplate.postForEntity<Void>("${properties.assignmentUrl}/api/admin/backfill", null)
    log.info("demo backfill requested")
    ready = true
  }

  @Scheduled(fixedDelayString = "\${ab.live-interval-ms:2000}")
  fun liveTick() {
    if (!ready) return
    val seq = liveSeq.getAndIncrement()
    DemoCatalog.experiments.forEach { effect ->
      try {
        val userId = "${effect.experimentKey}:live-$seq"
        val assignment = restTemplate.getForObject<AssignResponse>(
          "${properties.assignmentUrl}/api/assign?experiment_key={key}&user_id={user}",
          effect.experimentKey,
          userId,
        )
        if (!assignment.inExperiment || assignment.variantKey == null || assignment.assignedAt == null) return@forEach
        val sampled = OutcomeSampler.liveUser(effect, userId, assignment.variantKey, assignment.bucket)
        val exposedAt = Instant.parse(assignment.assignedAt)
        val events = outcomeEvents(effect.experimentKey, sampled, exposedAt)
        if (events.isNotEmpty()) {
          val headers = HttpHeaders().apply { contentType = MediaType.APPLICATION_JSON }
          restTemplate.postForEntity<Void>(
            "${properties.assignmentUrl}/api/outcomes/batch",
            HttpEntity(mapOf("events" to events), headers),
          )
        }
      } catch (ex: Exception) {
        log.warn("live tick failed for {}: {}", effect.experimentKey, ex.message)
      }
    }
  }

  private fun waitForAssignment() {
    repeat(60) {
      try {
        restTemplate.getForObject<Any>("${properties.assignmentUrl}/actuator/health")
        return
      } catch (_: Exception) {
        Thread.sleep(2_000)
      }
    }
    error("assignment-service is not healthy")
  }

  private fun outcomeEvents(experimentKey: String, user: SampledUser, exposedAt: Instant): List<OutcomeEvent> {
    val events = mutableListOf<OutcomeEvent>()
    if (user.preRevenue > 0.0) {
      events += outcome(experimentKey, user.userId, "revenue", user.preRevenue, exposedAt.minus(3, ChronoUnit.DAYS))
    }
    if (user.converted) {
      val at = exposedAt.plusSeconds(45)
      events += outcome(experimentKey, user.userId, "conversion", 1.0, at)
      events += outcome(experimentKey, user.userId, "revenue", user.revenue, at)
    }
    if (user.refunded) {
      events += outcome(experimentKey, user.userId, "refund", 1.0, exposedAt.plusSeconds(50))
    }
    return events
  }

  private fun outcome(
    experimentKey: String,
    userId: String,
    type: String,
    value: Double,
    at: Instant,
  ) = OutcomeEvent(
    experimentKey = experimentKey,
    userId = userId,
    eventType = type,
    value = value,
    eventTime = at.toString(),
  )
}
