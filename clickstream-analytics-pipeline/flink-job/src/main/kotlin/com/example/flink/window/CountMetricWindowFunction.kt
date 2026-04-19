package com.example.flink.window

import com.example.flink.model.EnrichedEvent
import com.example.flink.model.Metric
import com.example.flink.util.JsonUtils
import org.apache.flink.streaming.api.functions.windowing.ProcessWindowFunction
import org.apache.flink.streaming.api.windowing.windows.TimeWindow
import org.apache.flink.util.Collector

/**
 * Generic window function that emits a [Metric] JSON per (key, window) with the number of
 * events in the window. Reused for all simple count-based aggregations
 * (events per type, page popularity, hour-of-day activity).
 *
 * @param metricName logical name of the metric (ends up in the `metric` column downstream).
 * @param keyExtractor produces the bucket key string from the window key `K`.
 */
class CountMetricWindowFunction<K>(
  private val metricName: String,
  private val keyExtractor: (K) -> String = { it.toString() }
) : ProcessWindowFunction<EnrichedEvent, String, K, TimeWindow>() {

  override fun process(
    key: K,
    context: Context,
    elements: Iterable<EnrichedEvent>,
    out: Collector<String>
  ) {
    var total = 0L
    val it = elements.iterator()
    while (it.hasNext()) {
      it.next()
      total++
    }
    val metric = Metric(
      metric = metricName,
      key = keyExtractor(key),
      windowStart = context.window().start,
      windowEnd = context.window().end,
      value = total.toDouble()
    )
    out.collect(JsonUtils.toJson(metric))
  }
}
