package com.example.streams

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonProperty

@JsonIgnoreProperties(ignoreUnknown = true)
data class DebeziumEvent(
    val schema: Any?,
    val payload: Payload
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class Payload(
    val before: SensorData?,
    val after: SensorData?,
    @JsonProperty("op") val operation: String,
    @JsonProperty("ts_ms") val timestamp: Long?
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class SensorData(
    val id: Long?,
    @JsonProperty("device_id")
    val deviceId: String?,
    val humidity: Double?,
    val pressure: Double?,
    val temperature: Double?,
    val timestamp: String?
) 