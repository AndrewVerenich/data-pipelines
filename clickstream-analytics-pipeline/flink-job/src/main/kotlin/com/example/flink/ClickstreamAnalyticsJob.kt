package com.example.flink

import com.example.flink.model.ClickstreamEvent
import com.example.flink.model.EnrichedEvent
import com.example.flink.model.FraudRule
import com.example.flink.model.UserSegmentConfig
import com.example.flink.operator.ClickFraudDetector
import com.example.flink.operator.EventParserFunction
import com.example.flink.operator.FunnelAnalyzer
import com.example.flink.operator.SessionTracker
import com.example.flink.operator.UserSegmentEnricher
import com.example.flink.util.JsonUtils
import com.example.flink.util.KafkaUtils
import com.example.flink.window.CountMetricWindowFunction
import com.example.flink.window.UniqueUsersWindowFunction
import org.apache.flink.api.common.eventtime.WatermarkStrategy
import org.apache.flink.streaming.api.environment.CheckpointConfig
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment
import org.apache.flink.streaming.api.windowing.assigners.SlidingEventTimeWindows
import org.apache.flink.streaming.api.windowing.assigners.TumblingEventTimeWindows
import org.apache.flink.streaming.api.windowing.time.Time
import java.time.Duration

/**
 * Main Flink streaming job: E-commerce clickstream analytics.
 *
 * Graph layout (top-to-bottom):
 *
 * 1. `raw_events` (Kafka) -> [EventParserFunction] -> valid events + dead-letter side output.
 * 2. `user_segments` (Kafka) -> broadcast -> [UserSegmentEnricher] (BroadcastProcessFunction)
 *    attaches user segment to each event.
 * 3. The enriched stream fans out to:
 *    - [SessionTracker] - KeyedProcessFunction + ValueState + event-time timers (30 min gap).
 *    - [ClickFraudDetector] - KeyedBroadcastProcessFunction with `fraud_rules` broadcast
 *      stream; emits alerts via side output; clean events continue on the main channel.
 *    - [FunnelAnalyzer] - KeyedProcessFunction tracking the 5-step conversion funnel.
 *    - Windowed aggregations: events per type (tumbling 1m), page popularity (sliding 5m/1m),
 *      unique users per page (tumbling 1m), activity heatmap by hour-of-day (tumbling 1m).
 *
 * Every sink writes JSON-encoded records to its own Kafka topic, which is consumed by
 * ClickHouse Kafka Engine tables and visualised in Grafana.
 */
object ClickstreamAnalyticsJob {

  private const val TOPIC_RAW = "raw_events"
  private const val TOPIC_RULES = "fraud_rules"
  private const val TOPIC_SEGMENTS = "user_segments"

  private const val TOPIC_DEAD_LETTER = "dead_letter"
  private const val TOPIC_SESSION = "session_events"
  private const val TOPIC_FRAUD = "fraud_alerts"
  private const val TOPIC_FUNNEL = "funnel_events"
  private const val TOPIC_EVENTS_PER_TYPE = "events_per_type"
  private const val TOPIC_PAGE_VIEWS = "page_views"
  private const val TOPIC_UNIQUE_USERS = "unique_users_per_page"
  private const val TOPIC_HEATMAP = "activity_heatmap"

  @JvmStatic
  fun main(args: Array<String>) {
    val bootstrap = System.getenv("KAFKA_BOOTSTRAP") ?: "kafka:9092"

    val env = StreamExecutionEnvironment.getExecutionEnvironment()
    env.parallelism = 2
    env.enableCheckpointing(60_000)
    env.checkpointConfig.minPauseBetweenCheckpoints = 30_000
    env.checkpointConfig.checkpointTimeout = 120_000
    env.checkpointConfig.externalizedCheckpointCleanup =
      CheckpointConfig.ExternalizedCheckpointCleanup.RETAIN_ON_CANCELLATION
    env.config.autoWatermarkInterval = 200

    // --- 1. Parse raw events + split invalid records to a dead-letter side output. ---
    val rawStream = env.fromSource(
      KafkaUtils.createSource(bootstrap, TOPIC_RAW, "flink-analytics"),
      WatermarkStrategy.noWatermarks(),
      "raw-events-source"
    ).uid("raw-events-source").name("raw-events-source")

    val parsed = rawStream
      .process(EventParserFunction())
      .uid("event-parser")
      .name("Parse & Validate Events")

    parsed.getSideOutput(EventParserFunction.INVALID_EVENTS_TAG)
      .sinkTo(KafkaUtils.createSink(bootstrap, TOPIC_DEAD_LETTER))
      .uid("sink-dead-letter")
      .name("Sink: Dead Letter")

    val timedEvents = parsed.assignTimestampsAndWatermarks(
      WatermarkStrategy
        .forBoundedOutOfOrderness<ClickstreamEvent>(Duration.ofSeconds(5))
        .withTimestampAssigner { e, _ -> e.timestamp }
        .withIdleness(Duration.ofSeconds(30))
    )

    // --- 2. Enrich events with user segment via broadcast state. ---
    val segmentsStream = env.fromSource(
      KafkaUtils.createSource(bootstrap, TOPIC_SEGMENTS, "flink-segments"),
      WatermarkStrategy.noWatermarks(),
      "user-segments-source"
    ).uid("segments-source").name("user-segments-source")
      .map { JsonUtils.fromJson<UserSegmentConfig>(it) }
      .uid("segments-parse")
      .name("Parse Segment Configs")

    val segmentsBroadcast = segmentsStream.broadcast(UserSegmentEnricher.SEGMENT_DESCRIPTOR)

    val enrichedStream = timedEvents
      .connect(segmentsBroadcast)
      .process(UserSegmentEnricher())
      .uid("segment-enricher")
      .name("Enrich with User Segment")

    // --- 3a. Session tracking via KeyedProcessFunction + event-time timers. ---
    enrichedStream
      .keyBy { it.userId }
      .process(SessionTracker())
      .uid("session-tracker")
      .name("Session Tracker (30m gap)")
      .map { JsonUtils.toJson(it) }
      .uid("session-to-json")
      .name("Session -> JSON")
      .sinkTo(KafkaUtils.createSink(bootstrap, TOPIC_SESSION))
      .uid("sink-session")
      .name("Sink: session_events")

    // --- 3b. Fraud detection with broadcast rules + side output for alerts. ---
    val rulesStream = env.fromSource(
      KafkaUtils.createSource(bootstrap, TOPIC_RULES, "flink-rules"),
      WatermarkStrategy.noWatermarks(),
      "fraud-rules-source"
    ).uid("rules-source").name("fraud-rules-source")
      .map { JsonUtils.fromJson<FraudRule>(it) }
      .uid("rules-parse")
      .name("Parse Fraud Rules")

    val rulesBroadcast = rulesStream.broadcast(ClickFraudDetector.RULES_DESCRIPTOR)

    val fraudProcessed = enrichedStream
      .keyBy { it.userId }
      .connect(rulesBroadcast)
      .process(ClickFraudDetector())
      .uid("fraud-detector")
      .name("Click Fraud Detector")

    fraudProcessed.getSideOutput(ClickFraudDetector.FRAUD_ALERT_TAG)
      .sinkTo(KafkaUtils.createSink(bootstrap, TOPIC_FRAUD))
      .uid("sink-fraud")
      .name("Sink: fraud_alerts")

    // --- 3c. Funnel analysis with per-user state machine. ---
    enrichedStream
      .keyBy { it.userId }
      .process(FunnelAnalyzer())
      .uid("funnel-analyzer")
      .name("Funnel Analyzer (5 steps)")
      .sinkTo(KafkaUtils.createSink(bootstrap, TOPIC_FUNNEL))
      .uid("sink-funnel")
      .name("Sink: funnel_events")

    // --- 3d. Events per type (tumbling 1m, keyed by eventType). ---
    enrichedStream
      .keyBy { it.eventType }
      .window(TumblingEventTimeWindows.of(Time.minutes(1)))
      .process(CountMetricWindowFunction<String>("events_per_type"))
      .uid("agg-events-per-type")
      .name("Agg: events_per_type (1m)")
      .sinkTo(KafkaUtils.createSink(bootstrap, TOPIC_EVENTS_PER_TYPE))
      .uid("sink-events-per-type")
      .name("Sink: events_per_type")

    // --- 3e. Page popularity (sliding 5m / 1m, keyed by page). ---
    enrichedStream
      .keyBy { it.page }
      .window(SlidingEventTimeWindows.of(Time.minutes(5), Time.minutes(1)))
      .process(CountMetricWindowFunction<String>("page_views"))
      .uid("agg-page-views")
      .name("Agg: page_views (5m/1m)")
      .sinkTo(KafkaUtils.createSink(bootstrap, TOPIC_PAGE_VIEWS))
      .uid("sink-page-views")
      .name("Sink: page_views")

    // --- 3f. Unique users per page (tumbling 1m). ---
    enrichedStream
      .keyBy { it.page }
      .window(TumblingEventTimeWindows.of(Time.minutes(1)))
      .process(UniqueUsersWindowFunction<String>("unique_users_per_page"))
      .uid("agg-unique-users")
      .name("Agg: unique_users_per_page (1m)")
      .sinkTo(KafkaUtils.createSink(bootstrap, TOPIC_UNIQUE_USERS))
      .uid("sink-unique-users")
      .name("Sink: unique_users_per_page")

    // --- 3g. Activity heatmap by hour of day (UTC). ---
    enrichedStream
      .keyBy { ((it.timestamp / 1000 / 3600) % 24).toInt() }
      .window(TumblingEventTimeWindows.of(Time.minutes(1)))
      .process(CountMetricWindowFunction<Int>("activity_heatmap") { hour ->
        hour.toString().padStart(2, '0')
      })
      .uid("agg-heatmap")
      .name("Agg: activity_heatmap (1m)")
      .sinkTo(KafkaUtils.createSink(bootstrap, TOPIC_HEATMAP))
      .uid("sink-heatmap")
      .name("Sink: activity_heatmap")

    env.execute("E-commerce Clickstream Analytics")
  }
}
