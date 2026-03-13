package com.example.marketing.generator

import com.example.marketing.model.BackendEvent
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
class BackendEventGenerator(
  private val producer: EventProducer,
  @Value("\${app.generator.user-id-range}") private val userIdRange: Int,
  @Value("\${app.generator.product-id-range}") private val productIdRange: Int
) {
  private val log = LoggerFactory.getLogger(BackendEventGenerator::class.java)

  private val eventWeights = listOf(
    "registration" to 15,
    "order_completed" to 50,
    "payment_received" to 35
  )

  private val weightedEvents = eventWeights.flatMap { (type, weight) ->
    List(weight) { type }
  }

  @Scheduled(fixedDelayString = "\${app.generator.backend-interval-ms}")
  fun generate() {
    val userId = Random.nextLong(1, userIdRange.toLong() + 1)
    val eventType = weightedEvents.random()

    val orderId = if (eventType in listOf("order_completed", "payment_received"))
      "ORD-${UUID.randomUUID().toString().take(8).uppercase()}" else null

    val productId = if (eventType == "order_completed")
      Random.nextInt(1, productIdRange + 1) else null

    val amount = if (eventType in listOf("order_completed", "payment_received"))
      BigDecimal(Random.nextDouble(9.99, 500.00)).setScale(2, RoundingMode.HALF_UP) else null

    val event = BackendEvent(
      event_id = UUID.randomUUID().toString(),
      user_id = userId,
      event_type = eventType,
      order_id = orderId,
      product_id = productId,
      amount = amount,
      timestamp = Instant.now()
    )

    producer.send("marketing.backend_events", userId.toString(), event)
    log.info("Backend: {} user={} order={} amount={}", eventType, userId, orderId, amount)
  }
}
