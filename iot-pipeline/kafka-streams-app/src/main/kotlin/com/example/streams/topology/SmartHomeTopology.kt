package com.example.streams.topology

import com.example.streams.cdc.RoomConfigCdc
import com.example.streams.model.*
import com.example.streams.serde.jacksonSerde
import com.fasterxml.jackson.databind.ObjectMapper
import org.apache.kafka.common.serialization.Serdes
import org.apache.kafka.streams.KeyValue
import org.apache.kafka.streams.StreamsBuilder
import org.apache.kafka.streams.kstream.*
import java.time.Duration
import java.time.Instant
import java.util.*

object SmartHomeTopology {

  private const val CDC_ROOM = "iot.public.room_config"

  fun build(builder: StreamsBuilder, mapper: ObjectMapper) {
    val stringSerde = Serdes.String()
    val roomSerde = jacksonSerde(mapper, RoomConfig::class.java)
    val tempSerde = jacksonSerde(mapper, TemperatureReading::class.java)
    val motionSerde = jacksonSerde(mapper, MotionReading::class.java)
    val doorSerde = jacksonSerde(mapper, DoorWindowReading::class.java)
    val luxSerde = jacksonSerde(mapper, LightLevelReading::class.java)
    val avgSerde = jacksonSerde(mapper, AvgAgg::class.java)
    val motionLuxSerde = jacksonSerde(mapper, MotionLuxJoin::class.java)

    val roomTable: KTable<String, RoomConfig> = builder
      .stream(CDC_ROOM, Consumed.with(stringSerde, stringSerde))
      .mapValues { v -> RoomConfigCdc.parse(v) }
      .filter { _, c -> c != null }
      .mapValues { _, c -> c!! }
      .selectKey { _, c -> c.roomId }
      .toTable(Materialized.with(stringSerde, roomSerde))

    val tempStream = builder
      .stream("sensor.temperature", Consumed.with(stringSerde, stringSerde))
      .mapValues { j ->
        try {
          mapper.readValue(j, TemperatureReading::class.java)
        } catch (_: Exception) {
          null
        }
      }
      .filter { _, v -> v != null }
      .mapValues { _, v -> v!! }
      .selectKey { _, v -> v.roomId }

    val windowedClimate = tempStream
      .groupByKey(Grouped.with(stringSerde, tempSerde))
      .windowedBy(TimeWindows.ofSizeWithNoGrace(Duration.ofSeconds(30)))
      .aggregate(
        { AvgAgg() },
        { _, reading, agg ->
          agg.add(reading.temperature)
          agg
        },
        Materialized.with(stringSerde, avgSerde),
      )
      .suppress(Suppressed.untilWindowCloses(Suppressed.BufferConfig.unbounded()).withName("climate-suppress"))

    val climateAvgStream = windowedClimate
      .toStream()
      .map { wk, agg ->
        val roomId = wk.key()
        val payload = mapOf(
          "room_id" to roomId,
          "avg_temp" to agg.avg(),
          "window_start_ms" to wk.window().start(),
          "window_end_ms" to wk.window().end(),
        )
        KeyValue.pair(roomId, mapper.writeValueAsString(payload))
      }

    val hvacJsonStream = climateAvgStream
      .leftJoin(
        roomTable,
        ValueJoiner { avgJson: String?, cfg: RoomConfig? ->
          if (avgJson == null || cfg == null) {
            null
          } else {
            val node = mapper.readTree(avgJson)
            val avgTemp = node.get("avg_temp").asDouble()
            val action = decideHvac(avgTemp, cfg)
            val reason = String.format(
              Locale.US,
              "avg=%.2f desired=%s deadband=%s",
              avgTemp,
              cfg.desiredTemperature.toString(),
              cfg.climateDeadband.toString(),
            )
            mapper.writeValueAsString(
              mapOf(
                "room_id" to cfg.roomId,
                "action" to action,
                "reason" to reason,
                "ts" to Instant.now().toString(),
              ),
            )
          }
        },
      )
      .filter { _: String, v: String? -> v != null }
      .mapValues { _: String, v: String? -> v!! }

    hvacJsonStream.to("command.hvac", Produced.with(stringSerde, stringSerde))

    val analyticsStream = climateAvgStream
      .leftJoin(
        roomTable,
        ValueJoiner { avgJson: String?, cfg: RoomConfig? ->
          if (avgJson == null || cfg == null) {
            null
          } else {
            val node = mapper.readTree(avgJson)
            val avgTemp = node.get("avg_temp").asDouble()
            mapper.writeValueAsString(
              mapOf(
                "room_id" to cfg.roomId,
                "avg_temp" to avgTemp,
                "desired_temperature" to cfg.desiredTemperature,
                "ts" to Instant.now().toString(),
              ),
            )
          }
        },
      )
      .filter { _: String, v: String? -> v != null }
      .mapValues { _: String, v: String? -> v!! }

    analyticsStream.to("analytics.climate", Produced.with(stringSerde, stringSerde))

    hvacJsonStream.toTable(
      Materialized.`as`<String, String, org.apache.kafka.streams.state.KeyValueStore<org.apache.kafka.common.utils.Bytes, ByteArray>>("last-hvac-store")
        .withKeySerde(stringSerde)
        .withValueSerde(stringSerde),
    )

    val luxKeyed = builder
      .stream("sensor.light-level", Consumed.with(stringSerde, stringSerde))
      .mapValues { j ->
        try {
          mapper.readValue(j, LightLevelReading::class.java)
        } catch (_: Exception) {
          null
        }
      }
      .filter { _, v -> v != null }
      .mapValues { _, v -> v!! }
      .selectKey { _, v -> v.roomId }

    val luxTable: KTable<String, LightLevelReading> = luxKeyed
      .groupByKey(Grouped.with(stringSerde, luxSerde))
      .reduce { _, v -> v }

    val motionStream = builder
      .stream("sensor.motion", Consumed.with(stringSerde, stringSerde))
      .mapValues { j ->
        try {
          mapper.readValue(j, MotionReading::class.java)
        } catch (_: Exception) {
          null
        }
      }
      .filter { _, v -> v != null }
      .mapValues { _, v -> v!! }
      .selectKey { _, v -> v.roomId }

    val motionLux = motionStream.join(
      luxTable,
      ValueJoiner { mot: MotionReading, lux: LightLevelReading -> MotionLuxJoin(mot, lux) },
      Joined.with(stringSerde, motionSerde, luxSerde),
    )

    val lightingOnCtx = motionLux.join(
      roomTable,
      ValueJoiner { ml: MotionLuxJoin, cfg: RoomConfig ->
        LightingOnCtx(ml.motion, ml.lux, cfg)
      },
      Joined.with(stringSerde, motionLuxSerde, roomSerde),
    )

    val lightingOn = lightingOnCtx
      .filter { _: String, ctx: LightingOnCtx ->
        val cfg = ctx.cfg
        cfg.lightingMode.lowercase(Locale.getDefault()) == "auto" &&
          ctx.motion.detected &&
          ctx.lux.lux < cfg.luxOnThreshold
      }
      .mapValues { _: String, ctx: LightingOnCtx ->
        val cfg = ctx.cfg
        mapper.writeValueAsString(
          mapOf(
            "room_id" to cfg.roomId,
            "action" to "LIGHTS_ON",
            "reason" to "motion_and_lux_below_${cfg.luxOnThreshold}",
            "ts" to Instant.now().toString(),
          ),
        )
      }

    lightingOn.to("command.lighting", Produced.with(stringSerde, stringSerde))

    val lightingOff = luxKeyed
      .join(
        roomTable,
        ValueJoiner { lux: LightLevelReading, cfg: RoomConfig -> LuxAndConfig(lux, cfg) },
        Joined.with(stringSerde, luxSerde, roomSerde),
      )
      .filter { _: String, lc: LuxAndConfig ->
        val cfg = lc.cfg
        cfg.lightingMode.lowercase(Locale.getDefault()) == "auto" &&
          lc.lux.lux > cfg.luxOffThreshold
      }
      .mapValues { _: String, lc: LuxAndConfig ->
        val cfg = lc.cfg
        mapper.writeValueAsString(
          mapOf(
            "room_id" to cfg.roomId,
            "action" to "LIGHTS_OFF",
            "reason" to "lux_above_${cfg.luxOffThreshold}",
            "ts" to Instant.now().toString(),
          ),
        )
      }

    lightingOff.to("command.lighting", Produced.with(stringSerde, stringSerde))

    val doorStream = builder
      .stream("sensor.door-window", Consumed.with(stringSerde, stringSerde))
      .mapValues { j ->
        try {
          mapper.readValue(j, DoorWindowReading::class.java)
        } catch (_: Exception) {
          null
        }
      }
      .filter { _, v -> v != null }
      .mapValues { _, v -> v!! }
      .selectKey { _, v -> v.roomId }

    val doorAlerts = doorStream
      .join(
        roomTable,
        ValueJoiner { door: DoorWindowReading, cfg: RoomConfig -> DoorAndConfig(door, cfg) },
        Joined.with(stringSerde, doorSerde, roomSerde),
      )
      .filter { _: String, dc: DoorAndConfig ->
        dc.cfg.securityMode.lowercase(Locale.getDefault()) == "armed" &&
          dc.door.state.uppercase(Locale.getDefault()) == "OPEN"
      }
      .map { _: String, dc: DoorAndConfig ->
        val cfg = dc.cfg
        val payload = mapOf(
          "room_id" to cfg.roomId,
          "type" to "INTRUSION",
          "severity" to "HIGH",
          "detail" to "Door or window open while armed",
          "ts" to Instant.now().toString(),
        )
        KeyValue.pair(cfg.roomId, mapper.writeValueAsString(payload))
      }

    val motAlerts = motionStream
      .join(
        roomTable,
        ValueJoiner { mot: MotionReading, cfg: RoomConfig -> MotionAndConfig(mot, cfg) },
        Joined.with(stringSerde, motionSerde, roomSerde),
      )
      .filter { _: String, mc: MotionAndConfig ->
        mc.cfg.securityMode.lowercase(Locale.getDefault()) == "armed" && mc.motion.detected
      }
      .map { _: String, mc: MotionAndConfig ->
        val cfg = mc.cfg
        val payload = mapOf(
          "room_id" to cfg.roomId,
          "type" to "MOTION_WHILE_ARMED",
          "severity" to "MEDIUM",
          "detail" to "Motion while armed",
          "ts" to Instant.now().toString(),
        )
        KeyValue.pair(mc.motion.roomId, mapper.writeValueAsString(payload))
      }

    doorAlerts.merge(motAlerts).to("alert.security", Produced.with(stringSerde, stringSerde))
  }

  private fun decideHvac(avgTemp: Double, cfg: RoomConfig): String {
    val d = cfg.desiredTemperature
    val b = cfg.climateDeadband
    return when (cfg.hvacMode.lowercase(Locale.getDefault())) {
      "off" -> "IDLE"
      "heat" ->
        if (avgTemp < d - b) {
          "HEAT"
        } else {
          "IDLE"
        }
      "cool" ->
        if (avgTemp > d + b) {
          "COOL"
        } else {
          "IDLE"
        }
      else ->
        when {
          avgTemp < d - b -> "HEAT"
          avgTemp > d + b -> "COOL"
          else -> "IDLE"
        }
    }
  }
}
