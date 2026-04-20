package com.example.config

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import jakarta.annotation.PreDestroy
import org.apache.kafka.clients.producer.KafkaProducer
import org.apache.kafka.clients.producer.ProducerConfig
import org.apache.kafka.clients.producer.ProducerRecord
import org.apache.kafka.common.serialization.StringSerializer
import org.slf4j.LoggerFactory
import org.springframework.boot.CommandLineRunner
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication
import org.springframework.scheduling.annotation.EnableScheduling
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Service
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

private const val TOPIC_RULES = "fraud_rules"
private const val TOPIC_SEGMENTS = "user_segments"

@SpringBootApplication
@EnableScheduling
class ConfigPublisherApplication(private val publisher: ConfigPublisherService) : CommandLineRunner {
  private val log = LoggerFactory.getLogger(ConfigPublisherApplication::class.java)

  override fun run(vararg args: String?) {
    log.info("Spring Boot ConfigPublisher started")
    publisher.publishInitialSnapshot()
  }
}

fun main(args: Array<String>) {
  runApplication<ConfigPublisherApplication>(*args)
}

@Service
class ConfigPublisherService {
  private val log = LoggerFactory.getLogger(ConfigPublisherService::class.java)
  private val mapper = jacksonObjectMapper()
  private val rng = ThreadLocalRandom.current()
  private var iteration = 0

  private val producer: KafkaProducer<String, String> by lazy {
    createProducer(kafkaBootstrap())
  }

  fun publishInitialSnapshot() {
    log.info("ConfigPublisher bootstrapping Kafka configs, bootstrap={}", kafkaBootstrap())
    publishInitialFraudRules()
    publishInitialSegments()
    producer.flush()
    log.info("Initial config published: rules + segments")
  }

  @Scheduled(fixedDelayString = "\${config.publisher.update-interval-ms:60000}")
  fun rotateConfigs() {
    iteration++
    val userIndex = rng.nextInt(1, 101)
    val userId = "user_$userIndex"
    val segments = listOf("NEW", "RETURNING", "VIP")
    val newSegment = segments[rng.nextInt(segments.size)]
    publishSegment(UserSegmentConfig(userId, newSegment, System.currentTimeMillis()))
    log.info("Rotated segment: user={} -> {}", userId, newSegment)

    if (iteration % 5 == 0) {
      val newMaxClicks = 15 + rng.nextInt(11)
      publishRule(
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

  @PreDestroy
  fun closeProducer() {
    producer.close()
  }

  private fun kafkaBootstrap(): String = System.getenv("KAFKA_BOOTSTRAP") ?: "kafka:9092"

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

  private fun publishInitialFraudRules() {
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
    rules.forEach { publishRule(it) }
  }

  private fun publishInitialSegments() {
    val now = System.currentTimeMillis()
    for (i in 1..10) {
      publishSegment(UserSegmentConfig("user_$i", "VIP", now))
    }
    for (i in 11..50) {
      publishSegment(UserSegmentConfig("user_$i", "RETURNING", now))
    }
    for (i in 51..100) {
      publishSegment(UserSegmentConfig("user_$i", "NEW", now))
    }
  }

  private fun publishRule(rule: FraudRule) {
    val json = mapper.writeValueAsString(rule)
    producer.send(ProducerRecord(TOPIC_RULES, rule.ruleId, json))
  }

  private fun publishSegment(cfg: UserSegmentConfig) {
    val json = mapper.writeValueAsString(cfg)
    producer.send(ProducerRecord(TOPIC_SEGMENTS, cfg.userId, json))
  }
}
