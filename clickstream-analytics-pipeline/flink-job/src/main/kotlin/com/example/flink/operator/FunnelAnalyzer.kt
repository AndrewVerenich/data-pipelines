package com.example.flink.operator

import com.example.flink.model.EnrichedEvent
import com.example.flink.model.FunnelEvent
import com.example.flink.util.JsonUtils
import org.apache.flink.api.common.state.ValueState
import org.apache.flink.api.common.state.ValueStateDescriptor
import org.apache.flink.api.common.typeinfo.TypeHint
import org.apache.flink.api.common.typeinfo.TypeInformation
import org.apache.flink.configuration.Configuration
import org.apache.flink.streaming.api.functions.KeyedProcessFunction
import org.apache.flink.util.Collector

/**
 * Tracks a user through the e-commerce conversion funnel:
 * `page_view -> click -> add_to_cart -> checkout_start -> purchase`.
 *
 * Uses keyed state + event-time timers to emit ABANDONED if no progress happens within
 * [FUNNEL_TIMEOUT_MS], or COMPLETED on a successful purchase.
 */
class FunnelAnalyzer : KeyedProcessFunction<String, EnrichedEvent, String>() {

  companion object {
    const val FUNNEL_TIMEOUT_MS: Long = 60L * 60L * 1000L

    private val FUNNEL_STEPS = listOf(
      "page_view",
      "click",
      "add_to_cart",
      "checkout_start",
      "purchase"
    )

    private val STEP_LABELS = listOf("VIEW", "CLICK", "ADD_TO_CART", "CHECKOUT", "PURCHASE")

    private const val STATE_NAME = "funnel-state"
  }

  data class FunnelState(
    var currentStep: Int = -1,
    var funnelStartTime: Long = 0L,
    var lastStepTime: Long = 0L,
    var segment: String = "UNKNOWN",
    var timerTimestamp: Long = 0L
  )

  @Transient
  private lateinit var funnelState: ValueState<FunnelState>

  override fun open(parameters: Configuration) {
    funnelState = runtimeContext.getState(
      ValueStateDescriptor(
        STATE_NAME,
        TypeInformation.of(object : TypeHint<FunnelState>() {})
      )
    )
  }

  override fun processElement(
    event: EnrichedEvent,
    ctx: Context,
    out: Collector<String>
  ) {
    val stepIndex = FUNNEL_STEPS.indexOf(event.eventType)
    if (stepIndex < 0) return

    val existing = funnelState.value()

    if (existing == null) {
      if (stepIndex != 0) return
      val newTimer = event.timestamp + FUNNEL_TIMEOUT_MS
      val state = FunnelState(
        currentStep = 0,
        funnelStartTime = event.timestamp,
        lastStepTime = event.timestamp,
        segment = event.userSegment,
        timerTimestamp = newTimer
      )
      funnelState.update(state)
      ctx.timerService().registerEventTimeTimer(newTimer)
      emit(out, event.userId, STEP_LABELS[0], null, event.timestamp, state)
      return
    }

    if (stepIndex == existing.currentStep + 1) {
      val previousLabel = STEP_LABELS[existing.currentStep]
      existing.currentStep = stepIndex
      existing.lastStepTime = event.timestamp
      existing.segment = event.userSegment
      emit(out, event.userId, STEP_LABELS[stepIndex], previousLabel, event.timestamp, existing)

      if (stepIndex == FUNNEL_STEPS.size - 1) {
        emit(out, event.userId, "COMPLETED", STEP_LABELS[stepIndex], event.timestamp, existing)
        ctx.timerService().deleteEventTimeTimer(existing.timerTimestamp)
        funnelState.clear()
      } else {
        ctx.timerService().deleteEventTimeTimer(existing.timerTimestamp)
        val newTimer = event.timestamp + FUNNEL_TIMEOUT_MS
        existing.timerTimestamp = newTimer
        funnelState.update(existing)
        ctx.timerService().registerEventTimeTimer(newTimer)
      }
    } else if (stepIndex == 0) {
      ctx.timerService().deleteEventTimeTimer(existing.timerTimestamp)
      val newTimer = event.timestamp + FUNNEL_TIMEOUT_MS
      val state = FunnelState(
        currentStep = 0,
        funnelStartTime = event.timestamp,
        lastStepTime = event.timestamp,
        segment = event.userSegment,
        timerTimestamp = newTimer
      )
      funnelState.update(state)
      ctx.timerService().registerEventTimeTimer(newTimer)
      emit(out, event.userId, STEP_LABELS[0], null, event.timestamp, state)
    }
  }

  override fun onTimer(
    timestamp: Long,
    ctx: OnTimerContext,
    out: Collector<String>
  ) {
    val state = funnelState.value() ?: return
    if (state.timerTimestamp != timestamp) return
    emit(
      out,
      ctx.currentKey,
      "ABANDONED",
      STEP_LABELS.getOrNull(state.currentStep),
      state.lastStepTime,
      state
    )
    funnelState.clear()
  }

  private fun emit(
    out: Collector<String>,
    userId: String,
    step: String,
    previousStep: String?,
    stepTimestamp: Long,
    state: FunnelState
  ) {
    val funnelEvent = FunnelEvent(
      userId = userId,
      step = step,
      previousStep = previousStep,
      stepTimestamp = stepTimestamp,
      funnelStartTime = state.funnelStartTime,
      elapsedMs = stepTimestamp - state.funnelStartTime,
      segment = state.segment
    )
    out.collect(JsonUtils.toJson(funnelEvent))
  }
}
