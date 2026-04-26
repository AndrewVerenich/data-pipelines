package com.example.flink.operator

import com.example.flink.model.ClickstreamEvent
import com.example.flink.model.EnrichedEvent
import com.example.flink.model.UserSegmentConfig
import org.apache.flink.api.common.state.MapStateDescriptor
import org.apache.flink.api.common.typeinfo.Types
import org.apache.flink.streaming.api.functions.co.BroadcastProcessFunction
import org.apache.flink.util.Collector

/**
 * Enriches clickstream events with the user's current segment (NEW / RETURNING / VIP / UNKNOWN).
 *
 * Uses Flink's broadcast state pattern: segment updates arrive on a low-throughput side stream
 * and are broadcast to every parallel instance, allowing stateless keyed lookup without a join.
 */
class UserSegmentEnricher : BroadcastProcessFunction<ClickstreamEvent, UserSegmentConfig, EnrichedEvent>() {

  companion object {
    val SEGMENT_DESCRIPTOR: MapStateDescriptor<String, String> = MapStateDescriptor(
      "user-segments",
      Types.STRING,
      Types.STRING
    )
  }

  override fun processElement(
    event: ClickstreamEvent,
    ctx: ReadOnlyContext,
    out: Collector<EnrichedEvent>
  ) {
    val broadcastState = ctx.getBroadcastState(SEGMENT_DESCRIPTOR)
    val segment = broadcastState.get(event.userId) ?: "UNKNOWN"
    out.collect(EnrichedEvent.from(event, segment))
  }

  override fun processBroadcastElement(
    config: UserSegmentConfig,
    ctx: Context,
    out: Collector<EnrichedEvent>
  ) {
    if (config.userId.isBlank()) return
    val broadcastState = ctx.getBroadcastState(SEGMENT_DESCRIPTOR)
    broadcastState.put(config.userId, config.segment)
  }
}
