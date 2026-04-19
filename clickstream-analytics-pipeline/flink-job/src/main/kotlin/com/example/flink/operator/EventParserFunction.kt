package com.example.flink.operator

import com.example.flink.model.ClickstreamEvent
import com.example.flink.util.JsonUtils
import org.apache.flink.streaming.api.functions.ProcessFunction
import org.apache.flink.util.Collector
import org.apache.flink.util.OutputTag
import org.slf4j.LoggerFactory

/**
 * Parses raw JSON strings from Kafka into [ClickstreamEvent] objects.
 *
 * Valid events are emitted to the main output. Malformed JSON or records failing
 * schema validation are routed to a side output for a dead-letter queue.
 */
class EventParserFunction : ProcessFunction<String, ClickstreamEvent>() {

  companion object {
    val INVALID_EVENTS_TAG = object : OutputTag<String>("invalid-events") {}
    private val log = LoggerFactory.getLogger(EventParserFunction::class.java)
  }

  override fun processElement(
    value: String,
    ctx: Context,
    out: Collector<ClickstreamEvent>
  ) {
    if (value.isBlank()) return
    val parsed = JsonUtils.fromJsonOrNull<ClickstreamEvent>(value)
    if (parsed == null || !parsed.isValid()) {
      ctx.output(INVALID_EVENTS_TAG, buildDeadLetter(value, parsed))
      return
    }
    out.collect(parsed)
  }

  private fun buildDeadLetter(raw: String, parsed: ClickstreamEvent?): String {
    val reason = when {
      parsed == null -> "parse_error"
      parsed.userId.isBlank() -> "missing_userId"
      parsed.eventType.isBlank() -> "missing_eventType"
      parsed.page.isBlank() -> "missing_page"
      parsed.timestamp <= 0 -> "invalid_timestamp"
      else -> "unknown"
    }
    return JsonUtils.toJson(
      mapOf(
        "raw" to raw,
        "reason" to reason,
        "timestamp" to System.currentTimeMillis()
      )
    )
  }
}
