package com.example.ab.assignment

import com.example.ab.ExposureVersion
import com.fasterxml.jackson.annotation.JsonProperty
import org.slf4j.LoggerFactory
import org.springframework.kafka.core.KafkaTemplate
import org.springframework.stereotype.Component
import java.util.concurrent.TimeUnit

data class ExposureEvent(
    @JsonProperty("exposure_id") val exposureId: String,
    @JsonProperty("experiment_id") val experimentId: Int,
    @JsonProperty("experiment_key") val experimentKey: String,
    @JsonProperty("variant_key") val variantKey: String,
    @JsonProperty("user_id") val userId: String,
    val bucket: Int,
    @JsonProperty("exposed_at") val exposedAt: Double,
    @JsonProperty("exposure_version") val exposureVersion: Long,
)

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

fun exposureVersion(epochMillis: Long): Long = ExposureVersion.of(epochMillis)
