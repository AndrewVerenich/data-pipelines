package com.example.streams.stream

import com.example.streams.DebeziumEvent
import com.example.streams.serde.SensorAggregate
import com.example.streams.serde.SensorAggregateSerde
import com.example.streams.serde.SensorReading
import com.example.streams.serde.SensorReadingSerde
import com.example.streams.serde.fromDebeziumEvent
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.google.gson.Gson
import org.apache.kafka.common.serialization.Serdes
import org.apache.kafka.streams.StreamsBuilder
import org.apache.kafka.streams.kstream.Consumed
import org.apache.kafka.streams.kstream.Grouped
import org.apache.kafka.streams.kstream.KStream
import org.apache.kafka.streams.kstream.Materialized
import org.apache.kafka.streams.kstream.TimeWindows
import org.apache.kafka.streams.kstream.Windowed
import org.slf4j.LoggerFactory
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import java.time.Duration

@Configuration
class StreamConfig(
  private val kStreamBuilder: StreamsBuilder,
) {

  private val logger = LoggerFactory.getLogger(StreamConfig::class.java)

  @Bean
  fun aggregateIotStream(): KStream<Windowed<String?>?, String?> {
    return kStreamBuilder.stream("iot.public.sensor_data", Consumed.with(stringSerde, stringSerde))
      .mapValues { json -> deserializeJson(json) }
      .filter { _, message -> message != null }
      .mapValues { _, message -> message!! }
      .groupBy({ _, r -> r.deviceId }, Grouped.with(stringSerde, sensorReadingSerde))
      .windowedBy(TimeWindows.ofSizeWithNoGrace(Duration.ofSeconds(10)))
      .aggregate(
        { SensorAggregate() },
        { _, r, agg -> agg.add(r) },
        Materialized.with(Serdes.String(), SensorAggregateSerde())
      )
      .toStream()
      .mapValues { k, agg ->
        val startTime = k.window().startTime().toString().replace("Z", "")
        val endTime = k.window().endTime().toString().replace("Z", "")
        Gson().toJson(
          mapOf(
            "device_id" to k.key(),
            "window_start" to startTime,
            "window_end" to endTime,
            "avg_temp" to agg.avgTemp(),
            "avg_humidity" to agg.avgHum(),
            "avg_pressure" to agg.avgPres(),
            "count" to agg.getCount()
          )
        )
      }.also {
        it.to("sensor.aggregates")
      }
  }

  private fun deserializeJson(json: String): SensorReading? {
    return try {
      val debeziumEvent = mapper.readValue(json, DebeziumEvent::class.java)
      if (debeziumEvent.payload.operation == "c") {
        fromDebeziumEvent(debeziumEvent.payload)
      } else {
        null
      }
    } catch (e: Exception) {
      logger.warn("Failed to parse message: ${e.message}")
      null
    }
  }

  companion object {
    private val mapper = jacksonObjectMapper()
    private val stringSerde = Serdes.String()
    private val sensorReadingSerde = SensorReadingSerde()
  }
}