package com.example.marketing.generator

import com.example.marketing.model.WebsiteEvent
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
class WebsiteEventGenerator(
  private val producer: EventProducer,
  @Value("\${app.generator.user-id-range}") private val userIdRange: Int,
  @Value("\${app.generator.product-id-range}") private val productIdRange: Int
) {
  private val log = LoggerFactory.getLogger(WebsiteEventGenerator::class.java)

  private val pages = listOf(
    "/", "/products", "/products/detail", "/cart", "/checkout",
    "/account", "/about", "/blog", "/contact", "/search"
  )

  private val eventWeights = listOf(
    "page_view" to 40,
    "click" to 25,
    "add_to_cart" to 15,
    "purchase" to 10,
    "signup" to 10
  )

  private val weightedEvents = eventWeights.flatMap { (type, weight) ->
    List(weight) { type }
  }

  @Scheduled(fixedDelayString = "\${app.generator.website-interval-ms}")
  fun generate() {
    val userId = Random.nextLong(1, userIdRange.toLong() + 1)
    val eventType = weightedEvents.random()
    val productId = if (eventType in listOf("click", "add_to_cart", "purchase"))
      Random.nextInt(1, productIdRange + 1) else null
    val revenue = if (eventType == "purchase")
      BigDecimal(Random.nextDouble(9.99, 350.00)).setScale(2, RoundingMode.HALF_UP) else null

    val event = WebsiteEvent(
      event_id = UUID.randomUUID().toString(),
      user_id = userId,
      event_type = eventType,
      page_url = pages.random(),
      product_id = productId,
      revenue = revenue,
      session_id = "sess-${userId}-${System.currentTimeMillis() / 300000}",
      timestamp = Instant.now()
    )

    producer.send("marketing.website_events", event.user_id.toString(), event)
    log.info("Website: {} user={} product={}", eventType, userId, productId)
  }
}
