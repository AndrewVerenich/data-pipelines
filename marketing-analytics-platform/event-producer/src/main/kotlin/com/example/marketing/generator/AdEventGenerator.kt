package com.example.marketing.generator

import com.example.marketing.model.AdEvent
import com.example.marketing.producer.EventProducer
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.Instant
import java.util.*
import kotlin.random.Random

@Component
class AdEventGenerator(
  private val producer: EventProducer,
  @Value("\${app.generator.campaign-id-range}") private val campaignIdRange: Int,
  @Value("\${app.generator.user-id-range}") private val userIdRange: Int
) {
  private val log = LoggerFactory.getLogger(AdEventGenerator::class.java)

  private val platforms = listOf("google", "facebook", "tiktok", "instagram")

  private val eventWeights = listOf(
    "impression" to 60,
    "click" to 25,
    "conversion" to 15
  )

  private val weightedEvents = eventWeights.flatMap { (type, weight) ->
    List(weight) { type }
  }

  @Scheduled(fixedDelayString = "\${app.generator.ad-interval-ms}")
  fun generate() {
    val campaignId = Random.nextInt(1, campaignIdRange + 1)
    val platform = platforms.random()
    val eventType = weightedEvents.random()

    val cost = when (eventType) {
      "impression" -> BigDecimal(Random.nextDouble(0.01, 0.10))
      "click" -> BigDecimal(Random.nextDouble(0.20, 2.50))
      "conversion" -> BigDecimal(Random.nextDouble(1.00, 15.00))
      else -> BigDecimal.ZERO
    }.setScale(2, RoundingMode.HALF_UP)

    val revenue = if (eventType == "conversion")
      BigDecimal(Random.nextDouble(15.00, 350.00)).setScale(2, RoundingMode.HALF_UP) else null

    val userId = if (eventType in listOf("click", "conversion"))
      Random.nextLong(1, userIdRange.toLong() + 1) else null

    val event = AdEvent(
      event_id = UUID.randomUUID().toString(),
      campaign_id = campaignId,
      platform = platform,
      event_type = eventType,
      cost = cost,
      revenue = revenue,
      user_id = userId,
      timestamp = Instant.now()
    )

    producer.send("marketing.ad_events", campaignId.toString(), event)
    log.info("Ad: {} campaign={} platform={} cost={}", eventType, campaignId, platform, cost)
  }
}
