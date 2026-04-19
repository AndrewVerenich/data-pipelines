package com.example.config

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import org.apache.kafka.clients.producer.KafkaProducer
import org.apache.kafka.clients.producer.ProducerConfig
import org.apache.kafka.clients.producer.ProducerRecord
import org.apache.kafka.common.serialization.StringSerializer
import org.slf4j.LoggerFactory
import java.util.Properties
import java.util.concurrent.ThreadLocalRandom

/**
 * Publishes the broadcast configuration streams consumed by the Flink job:
 *
 * - `fraud_rules`: dynamic thresholds for the click-fraud detector.
 * - `user_segments`: userId -> segment (NEW / RETURNING / VIP) mapping.
 *
 * On startup, a full snapshot is published so Flink can boot with a complete picture.
 * Afterwards the publisher periodically rotates segments and tweaks fraud thresholds so
 * downstream dashboards visibly react to broadcast-state updates - the demo-friendly
 * equivalent of a real ops team pushing new configs at runtime.
 */
data class FraudRule(
  val ruleId: String,
  val ruleType: String,
  val eventType: String?,
  val maxCount: Int,
  val windowSeconds: Long,
  val active: Boolean = true
)

data class UserSegmentConfig(
  val userId: String,
  val segment: String,
  val updatedAt: Long
)

private val log = LoggerFactory.getLogger("ConfigPublisher")
private val mapper = jacksonObjectMapper()

private const val TOPIC_RULES = "fraud_rules"
private const val TOPIC_SEGMENTS = "user_segments"

fun main() {
  val bootstrap = System.getenv("KAFKA_BOOTSTRAP") ?: "kafka:9092"
  log.info("ConfigPublisher starting with bootstrap={}", bootstrap)

  val producer = createProducer(bootstrap)
  Runtime.getRuntime().addShutdownHook(Thread { producer.close() })

  publishInitialFraudRules(producer)
  publishInitialSegments(producer)
  producer.flush()
  log.info("Initial config published: rules + segments")

  val rng = ThreadLocalRandom.current()
  var iteration = 0
  while (true) {
    Thread.sleep(60_000)
    iteration++

    val userIndex = rng.nextInt(1, 101)
    val userId = "user_$userIndex"
    val newSegment = listOf("NEW", "RETURNING", "VIP").random()
    publishSegment(producer, UserSegmentConfig(userId, newSegment, System.currentTimeMillis()))
    log.info("Rotated segment: user={} -> {}", userId, newSegment)

    if (iteration % 5 == 0) {
      val newMaxClicks = 15 + rng.nextInt(11)
      publishRule(
        producer,
        FraudRule(
          ruleId = "rule-clicks",
          ruleType = "MAX_CLICKS_PER_WINDOW",
          eventType = "click",
          maxCount = newMaxClicks,
          windowSeconds = 60,
          active = true
        )
      )
      log.info("Updated fraud rule: rule-clicks maxCount={}", newMaxClicks)
    }

    producer.flush()
  }
}

private fun createProducer(bootstrap: String): KafkaProducer<String, String> {
  val props = Properties().apply {
    put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrap)
    put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer::class.java.name)
    put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer::class.java.name)
    put(ProducerConfig.ACKS_CONFIG, "all")
    put(ProducerConfig.RETRIES_CONFIG, 5)
    put(ProducerConfig.CLIENT_ID_CONFIG, "config-publisher")
  }
  return KafkaProducer(props)
}

private fun publishInitialFraudRules(producer: KafkaProducer<String, String>) {
  val rules = listOf(
    FraudRule(
      ruleId = "rule-clicks",
      ruleType = "MAX_CLICKS_PER_WINDOW",
      eventType = "click",
      maxCount = 15,
      windowSeconds = 60,
      active = true
    ),
    FraudRule(
      ruleId = "rule-rapid-purchases",
      ruleType = "RAPID_PURCHASES",
      eventType = "purchase",
      maxCount = 5,
      windowSeconds = 120,
      active = true
    ),
    FraudRule(
      ruleId = "rule-bot-scrape",
      ruleType = "MAX_PAGE_VIEWS_PER_WINDOW",
      eventType = "page_view",
      maxCount = 60,
      windowSeconds = 60,
      active = true
    )
  )
  rules.forEach { publishRule(producer, it) }
}

private fun publishInitialSegments(producer: KafkaProducer<String, String>) {
  val now = System.currentTimeMillis()
  for (i in 1..10) {
    publishSegment(producer, UserSegmentConfig("user_$i", "VIP", now))
  }
  for (i in 11..50) {
    publishSegment(producer, UserSegmentConfig("user_$i", "RETURNING", now))
  }
  for (i in 51..100) {
    publishSegment(producer, UserSegmentConfig("user_$i", "NEW", now))
  }
}

private fun publishRule(producer: KafkaProducer<String, String>, rule: FraudRule) {
  val json = mapper.writeValueAsString(rule)
  producer.send(ProducerRecord(TOPIC_RULES, rule.ruleId, json))
}

private fun publishSegment(producer: KafkaProducer<String, String>, cfg: UserSegmentConfig) {
  val json = mapper.writeValueAsString(cfg)
  producer.send(ProducerRecord(TOPIC_SEGMENTS, cfg.userId, json))
}
