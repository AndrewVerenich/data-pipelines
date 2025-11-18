package com.example.flink

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import org.apache.flink.api.common.eventtime.WatermarkStrategy
import org.apache.flink.api.common.serialization.SimpleStringSchema
import org.apache.flink.api.common.typeinfo.Types
import org.apache.flink.connector.kafka.sink.KafkaRecordSerializationSchema
import org.apache.flink.connector.kafka.sink.KafkaSink
import org.apache.flink.connector.kafka.source.KafkaSource
import org.apache.flink.streaming.api.datastream.DataStream
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment
import org.apache.flink.streaming.api.windowing.assigners.EventTimeSessionWindows
import org.apache.flink.streaming.api.windowing.assigners.SlidingEventTimeWindows
import org.apache.flink.streaming.api.windowing.assigners.TumblingEventTimeWindows
import org.apache.flink.streaming.api.windowing.time.Time
import java.time.Duration

object UserBehaviourAnalyticsJob {
  @JvmStatic
  fun main(args: Array<String>) {
    val env = StreamExecutionEnvironment.getExecutionEnvironment()
    env.parallelism = 1

    val bootstrap = System.getenv("KAFKA_BOOTSTRAP") ?: "kafka:9092"

    val source: KafkaSource<String> = KafkaSource.builder<String>()
      .setBootstrapServers(bootstrap)
      .setTopics("raw_events")
      .setGroupId("flink-analytics")
      .setValueOnlyDeserializer(SimpleStringSchema())
      .build()

    val raw: DataStream<String> = env.fromSource(
      source,
      WatermarkStrategy.noWatermarks(),
      "raw_events_source"
    )

    // Parse JSON and assign event-time watermarks
    val events: DataStream<UserEvent> = raw
      .filter { it.isNotBlank() }
      .map { mapper.readValue<UserEvent>(it) }
      .assignTimestampsAndWatermarks(
        WatermarkStrategy
          .forBoundedOutOfOrderness<UserEvent>(Duration.ofSeconds(5))
          .withTimestampAssigner { e, _ -> e.timestamp }
      )

    // Kafka sink factory
    fun sink(topic: String): KafkaSink<String?>? =
      KafkaSink.builder<String>()
        .setBootstrapServers(bootstrap)
        .setRecordSerializer(
          KafkaRecordSerializationSchema.builder<String>()
            .setTopic(topic)
            .setValueSerializationSchema(SimpleStringSchema())
            .build()
        ).build()

    // 1) Events per type (1-minute tumbling)
    events
      .map { KV(it.event, 1) }
      .keyBy { it.key }
      .window(TumblingEventTimeWindows.of(Time.minutes(1)))
      .apply { key, window, input, out ->
        val total = input.sumOf { it.value }
        val metric = Metric("events_per_type", key, window.start, window.end, total.toDouble())
        out.collect(toJson(metric))
      }
      .returns(Types.STRING)
      .sinkTo(sink("events_per_type"))

    // 2) Page views (top pages, 1-minute tumbling)
    events
      .map { KV(it.page, 1) }
      .keyBy { it.key }
      .window(TumblingEventTimeWindows.of(Time.minutes(1)))
      .apply { key, window, input, out ->
        val total = input.sumOf { it.value }
        val metric = Metric("page_views", key, window.start, window.end, total.toDouble())
        out.collect(toJson(metric))
      }
      .returns(Types.STRING)
      .sinkTo(sink("page_views"))

    // 3) Unique users per page (1-minute tumbling)
    events
      .keyBy { it.page }
      .window(TumblingEventTimeWindows.of(Time.minutes(1)))
      .apply { key, window, input, out ->
        val users = HashSet<String>()
        input.forEach { users.add(it.userId) }
        val metric = Metric("unique_users_per_page", key, window.start, window.end, users.size.toDouble())
        out.collect(toJson(metric))
      }
      .returns(Types.STRING)
      .sinkTo(sink("unique_users_per_page"))

    // 4) Conversion rate (view -> purchase) per 5-minute sliding window (slide 1m)
    events
      .keyBy { it.userId }
      .window(SlidingEventTimeWindows.of(Time.minutes(5), Time.minutes(1)))
      .apply { _, window, input, out ->
        var viewed = false
        var purchased = false
        input.forEach { ev ->
          if (ev.event == "view") viewed = true
          if (ev.event == "purchase") purchased = true
        }
        val conv = if (viewed) (if (purchased) 1.0 else 0.0) else 0.0
        val metric = Metric("conversion_rate_user", "user", window.start, window.end, conv)
        out.collect(toJson(metric))
      }
      .returns(Types.STRING)
      .keyBy { "global" }
      .window(TumblingEventTimeWindows.of(Time.minutes(1)))
      .apply { _, window, input, out ->
        var users = 0
        var converted = 0
        input.forEach { json ->
          val m = mapper.readValue<Metric>(json)
          users += 1
          converted += if (m.value >= 1.0) 1 else 0
        }
        val rate = if (users == 0) 0.0 else converted.toDouble() / users
        val metric = Metric("conversion_rate", "global", window.start, window.end, rate)
        out.collect(toJson(metric))
      }
      .returns(Types.STRING)
      .sinkTo(sink("conversion_rate"))

    // 5) Session stats per user using EventTimeSessionWindows (gap 30 min)
    events
      .keyBy { it.userId }
      .window(EventTimeSessionWindows.withGap(Time.minutes(30)))
      .apply { userId, _, input, out ->
        var minTs = Long.MAX_VALUE
        var maxTs = Long.MIN_VALUE
        var count = 0
        input.forEach { ev ->
          minTs = minOf(minTs, ev.timestamp)
          maxTs = maxOf(maxTs, ev.timestamp)
          count++
        }
        val duration = if (maxTs >= minTs) (maxTs - minTs) else 0L
        val session = SessionStat(userId, minTs, maxTs, duration, count)
        out.collect(toJson(session))
      }
      .returns(Types.STRING)
      .sinkTo(sink("session_stats"))

    // 6) Funnel stats per window: view -> click -> purchase (5 min tumbling)
    events
      .keyBy { it.userId }
      .window(TumblingEventTimeWindows.of(Time.minutes(5)))
      .apply { _, window, input, out ->
        var viewed = false
        var clicked = false
        var purchased = false
        val order = input.sortedBy { it.timestamp }
        order.forEach { ev ->
          when (ev.event) {
            "view" -> viewed = true
            "click" -> if (viewed) clicked = true
            "purchase" -> if (clicked) purchased = true
          }
        }
        val stat = FunnelStat(
          windowStart = window.start,
          windowEnd = window.end,
          viewed = if (order.any { it.event == "view" }) 1 else 0,
          clicked = if (order.any { it.event == "click" }) 1 else 0,
          purchased = if (order.any { it.event == "purchase" }) 1 else 0,
          completedVC = if (viewed && clicked) 1 else 0,
          completedCP = if (clicked && purchased) 1 else 0,
          completedVCP = if (viewed && clicked && purchased) 1 else 0
        )
        out.collect(toJson(stat))
      }
      .returns(Types.STRING)
      .keyBy { "global" }
      .window(TumblingEventTimeWindows.of(Time.minutes(5)))
      .reduce({ a, b ->
        val sa = mapper.readValue<FunnelStat>(a)
        val sb = mapper.readValue<FunnelStat>(b)
        toJson(
          sa.copy(
            viewed = sa.viewed + sb.viewed,
            clicked = sa.clicked + sb.clicked,
            purchased = sa.purchased + sb.purchased,
            completedVC = sa.completedVC + sb.completedVC,
            completedCP = sa.completedCP + sb.completedCP,
            completedVCP = sa.completedVCP + sb.completedVCP
          )
        )
      })
      .returns(Types.STRING)
      .sinkTo(sink("funnel_stats"))

    // 7) Activity heatmap by hour of day (1-minute tumbling)
    events
      .map { HeatBucket(hour = ((it.timestamp / 1000 / 3600) % 24).toInt(), count = 1) }
      .keyBy { it.hour }
      .window(TumblingEventTimeWindows.of(Time.minutes(1)))
      .apply { hour, window, input, out ->
        val total = input.sumOf { it.count }
        val metric = Metric(
          metric = "activity_heatmap",
          key = hour.toString(),
          windowStart = window.start,
          windowEnd = window.end,
          value = total.toDouble()
        )
        out.collect(toJson(metric))
      }
      .returns(Types.STRING)
      .sinkTo(sink("activity_heatmap"))


    // 8) Retention (returning vs new users in 1-hour tumbling)
    // Примерно: если у пользователя в окне более одной сессии или событие ранее уже было — считаем returning.
    events
      .keyBy { it.userId }
      .window(TumblingEventTimeWindows.of(Time.hours(1)))
      .apply { _, _, input, out ->
        val count = input.count()
        val returning = if (count > 1) 1 else 0
        val newUser = if (count == 1) 1 else 0
        val json = toJson(mapOf("returning" to returning, "new" to newUser, "total" to 1))
        out.collect(json)
      }
      .returns(Types.STRING)
      .keyBy { "global" }
      .window(TumblingEventTimeWindows.of(Time.hours(1)))
      .apply { _, window, input, out ->
        var returning = 0L
        var newUsers = 0L
        var total = 0L
        input.forEach { js ->
          val m = mapper.readValue<Map<String, Int>>(js)
          returning += m["returning"] ?: 0
          newUsers += m["new"] ?: 0
          total += m["total"] ?: 0
        }
        val ret = Retention(window.start, window.end, returning, newUsers, total)
        out.collect(toJson(ret))
      }
      .returns(Types.STRING)
      .sinkTo(sink("retention"))

    env.execute("User Behaviour Analytics")
  }
}

data class UserEvent(
  val userId: String,
  val event: String,
  val page: String,
  val timestamp: Long
)

data class KV(val key: String, val value: Long)
data class Metric(val metric: String, val key: String, val windowStart: Long, val windowEnd: Long, val value: Double)
data class SessionStat(
  val userId: String,
  val sessionStart: Long,
  val sessionEnd: Long,
  val durationMs: Long,
  val events: Int
)

data class FunnelStat(
  val windowStart: Long,
  val windowEnd: Long,
  val viewed: Long,
  val clicked: Long,
  val purchased: Long,
  val completedVC: Long,
  val completedCP: Long,
  val completedVCP: Long
)

data class HeatBucket(val hour: Int, val count: Long)
data class Retention(
  val windowStart: Long,
  val windowEnd: Long,
  val returningUsers: Long,
  val newUsers: Long,
  val totalUsers: Long
)

private val mapper = jacksonObjectMapper()

fun toJson(any: Any): String = mapper.writeValueAsString(any)
