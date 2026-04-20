package com.example.flink.operator

import com.example.flink.model.EnrichedEvent
import com.example.flink.model.SessionEvent
import org.apache.flink.api.common.state.ValueState
import org.apache.flink.api.common.state.ValueStateDescriptor
import org.apache.flink.api.common.typeinfo.TypeHint
import org.apache.flink.api.common.typeinfo.TypeInformation
import org.apache.flink.configuration.Configuration
import org.apache.flink.streaming.api.functions.KeyedProcessFunction
import org.apache.flink.util.Collector
import java.util.UUID

/**
 * Tracks user sessions using keyed state and event-time timers.
 *
 * A session opens on the first event per user and is extended with every subsequent event.
 * If no event arrives within [SESSION_TIMEOUT_MS] (event time), the session is closed
 * and a `session_end` record is emitted carrying duration, page trail, and event count.
 */
class SessionTracker : KeyedProcessFunction<String, EnrichedEvent, SessionEvent>() {

  companion object {
    // Keep timeout short enough for demo traffic so session_end events are emitted regularly.
    const val SESSION_TIMEOUT_MS: Long = 90L * 1000L
    private const val STATE_NAME = "session-state"
  }

  /**
   * Per-user session state. Kept mutable for in-place updates since Flink reads/writes
   * the full value via [ValueState].
   */
  data class SessionState(
    var sessionId: String = "",
    var startTime: Long = 0L,
    var lastEventTime: Long = 0L,
    var eventCount: Int = 0,
    var pages: MutableList<String> = mutableListOf(),
    var segment: String = "UNKNOWN",
    var timerTimestamp: Long = 0L
  )

  @Transient
  private lateinit var sessionState: ValueState<SessionState>

  override fun open(parameters: Configuration) {
    val descriptor = ValueStateDescriptor(
      STATE_NAME,
      TypeInformation.of(object : TypeHint<SessionState>() {})
    )
    sessionState = runtimeContext.getState(descriptor)
  }

  override fun processElement(
    event: EnrichedEvent,
    ctx: Context,
    out: Collector<SessionEvent>
  ) {
    val existing = sessionState.value()
    val now = event.timestamp
    val newTimer = now + SESSION_TIMEOUT_MS

    if (existing == null) {
      val state = SessionState(
        sessionId = UUID.randomUUID().toString(),
        startTime = now,
        lastEventTime = now,
        eventCount = 1,
        pages = mutableListOf(event.page),
        segment = event.userSegment,
        timerTimestamp = newTimer
      )
      sessionState.update(state)
      ctx.timerService().registerEventTimeTimer(newTimer)

      out.collect(
        SessionEvent(
          sessionId = state.sessionId,
          userId = event.userId,
          eventType = "session_start",
          startTime = state.startTime,
          endTime = state.lastEventTime,
          eventCount = state.eventCount,
          pages = state.pages.toList(),
          segment = state.segment,
          durationMs = 0L
        )
      )
    } else {
      ctx.timerService().deleteEventTimeTimer(existing.timerTimestamp)
      existing.lastEventTime = now
      existing.eventCount += 1
      if (existing.pages.lastOrNull() != event.page) {
        existing.pages.add(event.page)
      }
      if (event.userSegment != "UNKNOWN") existing.segment = event.userSegment
      existing.timerTimestamp = newTimer
      sessionState.update(existing)
      ctx.timerService().registerEventTimeTimer(newTimer)
    }
  }

  override fun onTimer(
    timestamp: Long,
    ctx: OnTimerContext,
    out: Collector<SessionEvent>
  ) {
    val state = sessionState.value() ?: return
    if (state.timerTimestamp != timestamp) return

    out.collect(
      SessionEvent(
        sessionId = state.sessionId,
        userId = ctx.currentKey,
        eventType = "session_end",
        startTime = state.startTime,
        endTime = state.lastEventTime,
        eventCount = state.eventCount,
        pages = state.pages.toList(),
        segment = state.segment,
        durationMs = state.lastEventTime - state.startTime
      )
    )
    sessionState.clear()
  }
}
