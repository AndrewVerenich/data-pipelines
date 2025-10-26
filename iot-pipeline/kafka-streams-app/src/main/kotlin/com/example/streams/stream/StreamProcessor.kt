package com.example.streams.stream

import com.example.streams.DebeziumEvent
import com.example.streams.serde.SensorAggregate
import com.example.streams.serde.SensorAggregateSerde
import com.example.streams.serde.SensorReadingSerde
import com.example.streams.serde.fromDebeziumEvent
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.google.gson.Gson
import org.apache.kafka.common.serialization.Serdes
import org.apache.kafka.streams.StreamsBuilder
import org.apache.kafka.streams.kstream.Grouped
import org.apache.kafka.streams.kstream.KStream
import org.apache.kafka.streams.kstream.Materialized
import org.apache.kafka.streams.kstream.TimeWindows
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.core.env.Environment
import org.springframework.stereotype.Component
import java.time.Duration

@Component
class StreamProcessor {

  val logger = LoggerFactory.getLogger(StreamProcessor::class.java)

  @Autowired
  fun build(builder: StreamsBuilder, env: Environment) {
    val stream: KStream<String, String> = builder.stream("iot.public.sensor_data")
    val mapper = jacksonObjectMapper()
    val stringSerde = Serdes.String()
    val sensorReadingSerde = SensorReadingSerde()

    stream
      .mapValues { json ->
        try {
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
      .filter { _, r -> r != null }
      .filter { _, r -> r?.deviceId != null }
      .mapValues { _, r -> r!! }
      .groupBy ({ _, r -> r.deviceId }, Grouped.with(stringSerde, sensorReadingSerde))
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
      }
      .to("sensor.aggregates")
  }
}