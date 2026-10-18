package com.example.ab

import com.google.common.hash.Hashing
import java.nio.charset.StandardCharsets

object Bucketer {
    const val BUCKETS = 10_000

    fun bucket(salt: String, userId: String): Int {
        val hash = Hashing.murmur3_32_fixed()
            .hashString("$salt:$userId", StandardCharsets.UTF_8)
            .asInt()
        return ((hash.toLong() and 0xffffffffL) % BUCKETS).toInt()
    }

    /**
     * Splits buckets `[0, trafficPct * 100)` by variant weights.
     * Variants are ordered by key so the same config always maps a bucket to the same variant.
     */
    fun variant(bucket: Int, trafficPct: Int, variants: List<Pair<String, Int>>): String? {
        val span = trafficPct.coerceIn(0, 100) * 100
        if (span == 0 || bucket !in 0 until span) return null
        val ordered = variants.sortedBy { it.first }
        val total = ordered.sumOf { it.second }
        if (total <= 0) return null
        val position = (bucket.toLong() * total) / span
        var cursor = 0
        for ((key, weight) in ordered) {
            cursor += weight
            if (position < cursor) return key
        }
        return ordered.last().first
    }
}
