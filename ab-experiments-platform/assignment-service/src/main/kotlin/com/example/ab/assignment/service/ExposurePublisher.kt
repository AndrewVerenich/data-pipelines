package com.example.ab.assignment.service

import com.example.ab.assignment.config.AssignmentProperties
import com.example.ab.assignment.model.ExposureEvent
import org.slf4j.LoggerFactory
import org.springframework.kafka.core.KafkaTemplate
import org.springframework.stereotype.Component
import java.util.concurrent.TimeUnit

@Component
class ExposurePublisher(
  private val kafkaTemplate: KafkaTemplate<String, Any>,
  private val properties: AssignmentProperties,
) {
  private val log = LoggerFactory.getLogger(ExposurePublisher::class.java)

  fun publish(event: ExposureEvent) {
    kafkaTemplate.send(properties.exposuresTopic, event.userId, event)
      .get(5, TimeUnit.SECONDS)
    log.debug("exposure {} {} -> {}", event.experimentKey, event.userId, event.variantKey)
  }
}
