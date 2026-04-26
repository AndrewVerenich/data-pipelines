package com.example.flink.operator

import com.example.flink.model.EnrichedEvent
import com.example.flink.model.FraudAlert
import com.example.flink.model.FraudRule
import com.example.flink.util.JsonUtils
import org.apache.flink.api.common.state.MapStateDescriptor
import org.apache.flink.api.common.state.ValueState
import org.apache.flink.api.common.state.ValueStateDescriptor
import org.apache.flink.api.common.typeinfo.TypeHint
import org.apache.flink.api.common.typeinfo.TypeInformation
import org.apache.flink.api.common.typeinfo.Types
import org.apache.flink.configuration.Configuration
import org.apache.flink.streaming.api.functions.co.KeyedBroadcastProcessFunction
import org.apache.flink.util.Collector
import org.apache.flink.util.OutputTag

/**
 * Real-time fraud detection combining per-user keyed state with a broadcast stream of rules.
 *
 * Rules (e.g. "more than N clicks in W seconds") arrive on a low-throughput side stream and
 * are broadcast to every parallel instance. For each event, we maintain sliding counters per
 * (rule, event type) and emit a [FraudAlert] to a side output when a threshold is breached.
 * Clean events are forwarded to the main output unchanged.
 */
class ClickFraudDetector :
  KeyedBroadcastProcessFunction<String, EnrichedEvent, FraudRule, EnrichedEvent>() {

  companion object {
    val FRAUD_ALERT_TAG = object : OutputTag<String>("fraud-alerts") {}

    val RULES_DESCRIPTOR: MapStateDescriptor<String, FraudRule> = MapStateDescriptor(
      "fraud-rules",
      Types.STRING,
      TypeInformation.of(FraudRule::class.java)
    )

    private const val COUNTERS_STATE = "fraud-counters"
    private const val ALERTED_STATE = "fraud-alerted"
  }

  /**
   * Sliding counter per (ruleId, eventType) key for a single user.
   */
  data class Counter(
    var count: Int = 0,
    var windowStart: Long = 0L
  )

  @Transient
  private lateinit var counters: ValueState<MutableMap<String, Counter>>

  @Transient
  private lateinit var alerted: ValueState<MutableMap<String, Long>>

  override fun open(parameters: Configuration) {
    counters = runtimeContext.getState(
      ValueStateDescriptor(
        COUNTERS_STATE,
        TypeInformation.of(object : TypeHint<MutableMap<String, Counter>>() {})
      )
    )
    alerted = runtimeContext.getState(
      ValueStateDescriptor(
        ALERTED_STATE,
        TypeInformation.of(object : TypeHint<MutableMap<String, Long>>() {})
      )
    )
  }

  override fun processElement(
    event: EnrichedEvent,
    ctx: ReadOnlyContext,
    out: Collector<EnrichedEvent>
  ) {
    val rules = ctx.getBroadcastState(RULES_DESCRIPTOR)
    val countersMap = counters.value() ?: mutableMapOf()
    val alertedMap = alerted.value() ?: mutableMapOf()

    var isFraud = false

    for (entry in rules.immutableEntries()) {
      val rule = entry.value
      if (!rule.active) continue
      if (rule.eventType != null && rule.eventType != event.eventType) continue

      val stateKey = "${rule.ruleId}|${rule.eventType ?: "*"}"
      val counter = countersMap[stateKey] ?: Counter(0, event.timestamp)
      val windowMs = rule.windowSeconds * 1000L

      if (event.timestamp - counter.windowStart >= windowMs) {
        counter.count = 1
        counter.windowStart = event.timestamp
      } else {
        counter.count += 1
      }
      countersMap[stateKey] = counter

      if (counter.count > rule.maxCount) {
        val cooldownEnd = alertedMap[rule.ruleId] ?: 0L
        if (event.timestamp >= cooldownEnd) {
          val alert = FraudAlert(
            userId = event.userId,
            ruleId = rule.ruleId,
            ruleType = rule.ruleType,
            eventType = event.eventType,
            eventCount = counter.count,
            windowStart = counter.windowStart,
            windowEnd = counter.windowStart + windowMs,
            timestamp = event.timestamp,
            segment = event.userSegment
          )
          ctx.output(FRAUD_ALERT_TAG, JsonUtils.toJson(alert))
          alertedMap[rule.ruleId] = event.timestamp + windowMs
        }
        isFraud = true
      }
    }

    counters.update(countersMap)
    alerted.update(alertedMap)

    if (!isFraud) {
      out.collect(event)
    }
  }

  override fun processBroadcastElement(
    rule: FraudRule,
    ctx: Context,
    out: Collector<EnrichedEvent>
  ) {
    if (rule.ruleId.isBlank()) return
    ctx.getBroadcastState(RULES_DESCRIPTOR).put(rule.ruleId, rule)
  }
}
