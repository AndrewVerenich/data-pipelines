package com.example.ab.simulator.service

import com.example.ab.bucketing.ExposureVersion
import com.example.ab.catalog.DemoCatalog
import com.example.ab.catalog.PlantedEffect
import com.example.ab.clickhouse.ClickHouseClient
import com.example.ab.sampling.OutcomeSampler
import com.example.ab.sampling.SampledUser
import com.example.ab.simulator.config.SimulatorProperties
import com.example.ab.simulator.model.AssignResponse
import com.example.ab.simulator.model.OutcomeEvent
import com.fasterxml.jackson.databind.ObjectMapper
import org.slf4j.LoggerFactory
import org.springframework.boot.ApplicationArguments
import org.springframework.boot.ApplicationRunner
import org.springframework.kafka.core.KafkaTemplate
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import org.springframework.web.client.getForObject
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.UUID
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong

@Component
class TrafficSimulator(
  private val properties: SimulatorProperties,
  private val kafkaTemplate: KafkaTemplate<String, Any>,
  private val objectMapper: ObjectMapper,
  restTemplateBuilder: org.springframework.boot.web.client.RestTemplateBuilder,
) : ApplicationRunner {
  private val log = LoggerFactory.getLogger(TrafficSimulator::class.java)
  private val clickHouse = ClickHouseClient(
    properties.clickhouseUrl,
    properties.clickhouseUser,
    properties.clickhousePassword,
  )
  private val restTemplate = restTemplateBuilder
    .setConnectTimeout(java.time.Duration.ofSeconds(3))
    .setReadTimeout(java.time.Duration.ofSeconds(10))
    .build()
  private val liveSeq = AtomicLong(1)
  @Volatile
  private var backfillDone = false

  override fun run(args: ApplicationArguments) {
    backfill()
    backfillDone = true
  }

  @Scheduled(fixedDelayString = "\${ab.live-interval-ms:2000}")
  fun liveTick() {
    if (!backfillDone) return
    val seq = liveSeq.getAndIncrement()
    DemoCatalog.experiments.forEach { effect ->
      try {
        val userId = "${effect.experimentKey}:live-$seq"
        val assignment = restTemplate.getForObject<AssignResponse>(
          "${properties.assignmentUrl}/api/assign?experiment_key={key}&user_id={user}",
          effect.experimentKey,
          userId,
        )
        if (!assignment.inExperiment || assignment.variantKey == null || assignment.exposedAt == null) return@forEach
        val sampled = OutcomeSampler.liveUser(effect, userId, assignment.variantKey, assignment.bucket)
        val exposedAt = Instant.ofEpochMilli((assignment.exposedAt * 1000).toLong())
        outcomeEvents(sampled, exposedAt).forEach { event ->
          kafkaTemplate.send(properties.outcomesTopic, event.userId, event).get(5, TimeUnit.SECONDS)
        }
      } catch (ex: Exception) {
        log.warn("live tick failed for {}: {}", effect.experimentKey, ex.message)
      }
    }
  }

  private fun backfill() {
    val existing = clickHouse.command(
      "SELECT count() FROM ab.raw_exposures WHERE user_id LIKE '%:u-%' FORMAT TabSeparated"
    ).trim().toLongOrNull() ?: 0L
    if (existing > 0L) {
      log.info("backfill already present ({} exposures), skipping", existing)
      return
    }
    val start = Instant.now().minus(14, ChronoUnit.DAYS)
    val spanMillis = 14L * 24 * 60 * 60 * 1000
    DemoCatalog.experiments.forEach { effect ->
      val exposures = mutableListOf<String>()
      val outcomes = mutableListOf<String>()
      repeat(DemoCatalog.BACKFILL_USERS) { index ->
        val user = OutcomeSampler.backfillUser(effect, index)
        val exposedAt = start.plusMillis(index * spanMillis / DemoCatalog.BACKFILL_USERS)
        exposures += objectMapper.writeValueAsString(exposureRow(effect, user, exposedAt))
        outcomeEvents(user, exposedAt).forEach { event ->
          outcomes += objectMapper.writeValueAsString(outcomeRow(event))
        }
      }
      clickHouse.insertJsonEachRow("ab.raw_exposures", exposures)
      clickHouse.insertJsonEachRow("ab.raw_outcomes", outcomes)
      log.info("backfilled {} users for {}", DemoCatalog.BACKFILL_USERS, effect.experimentKey)
    }
  }

  private fun exposureRow(effect: PlantedEffect, user: SampledUser, exposedAt: Instant): Map<String, Any> {
    val millis = exposedAt.toEpochMilli()
    return mapOf(
      "exposure_id" to "backfill-${user.userId}",
      "experiment_id" to effect.experimentId,
      "experiment_key" to effect.experimentKey,
      "variant_key" to user.variantKey,
      "user_id" to user.userId,
      "bucket" to user.bucket,
      "exposed_at" to CLICKHOUSE_TIME.format(exposedAt),
      "exposure_version" to ExposureVersion.of(millis),
    )
  }

  private fun outcomeEvents(user: SampledUser, exposedAt: Instant): List<OutcomeEvent> {
    val events = mutableListOf<OutcomeEvent>()
    if (user.preRevenue > 0.0) {
      events += outcome(user.userId, "revenue", user.preRevenue, exposedAt.minus(3, ChronoUnit.DAYS))
    }
    if (user.converted) {
      val at = exposedAt.plusSeconds(45)
      events += outcome(user.userId, "conversion", 1.0, at)
      events += outcome(user.userId, "revenue", user.revenue, at)
    }
    if (user.refunded) {
      events += outcome(user.userId, "refund", 1.0, exposedAt.plusSeconds(50))
    }
    return events
  }

  private fun outcome(userId: String, type: String, value: Double, at: Instant) = OutcomeEvent(
    eventId = UUID.randomUUID().toString(),
    userId = userId,
    eventType = type,
    value = value,
    eventTime = at.toEpochMilli() / 1000.0,
  )

  private fun outcomeRow(event: OutcomeEvent): Map<String, Any> = mapOf(
    "event_id" to event.eventId,
    "user_id" to event.userId,
    "event_type" to event.eventType,
    "value" to event.value,
    "event_time" to CLICKHOUSE_TIME.format(Instant.ofEpochMilli((event.eventTime * 1000).toLong())),
  )

  private companion object {
    val CLICKHOUSE_TIME: DateTimeFormatter =
      DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS").withZone(ZoneOffset.UTC)
  }
}
