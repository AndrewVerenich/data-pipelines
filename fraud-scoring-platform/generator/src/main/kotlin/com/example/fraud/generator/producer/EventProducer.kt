package com.example.fraud.generator.producer

import org.slf4j.LoggerFactory
import org.springframework.kafka.core.KafkaTemplate
import org.springframework.stereotype.Component

@Component
class EventProducer(private val kafkaTemplate: KafkaTemplate<String, Any>) {

  private val log = LoggerFactory.getLogger(javaClass)

  fun send(topic: String, key: String, event: Any) {
    kafkaTemplate.send(topic, key, event).whenComplete { _, ex ->
      if (ex != null) {
        log.error("Failed to send to {}: {}", topic, ex.message)
      }
    }
  }
}
