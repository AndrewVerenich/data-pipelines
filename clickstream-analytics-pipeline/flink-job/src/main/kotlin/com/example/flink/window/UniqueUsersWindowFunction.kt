package com.example.flink.window

import com.example.flink.model.EnrichedEvent
import com.example.flink.model.Metric
import com.example.flink.util.JsonUtils
import org.apache.flink.streaming.api.functions.windowing.ProcessWindowFunction
import org.apache.flink.streaming.api.windowing.windows.TimeWindow
import org.apache.flink.util.Collector

/**
 * Counts distinct `userId` values in a window, keyed by some grouping (e.g. page).
 * Emits a [Metric] with the distinct count as `value`.
 */
class UniqueUsersWindowFunction<K>(
  private val metricName: String,
  private val keyExtractor: (K) -> String = { it.toString() }
) : ProcessWindowFunction<EnrichedEvent, String, K, TimeWindow>() {

  override fun process(
    key: K,
    context: Context,
    elements: Iterable<EnrichedEvent>,
    out: Collector<String>
  ) {
    val distinct = HashSet<String>()
    elements.forEach { distinct.add(it.userId) }
    val metric = Metric(
      metric = metricName,
      key = keyExtractor(key),
      windowStart = context.window().start,
      windowEnd = context.window().end,
      value = distinct.size.toDouble()
    )
    out.collect(JsonUtils.toJson(metric))
  }
}
