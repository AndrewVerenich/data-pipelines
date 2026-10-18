package com.example.ab

object ExposureVersion {
    private const val EPOCH_BASE = 10_000_000_000_000L

    /** Earlier exposure gets the larger version so ReplacingMergeTree keeps the first one. */
    fun of(epochMillis: Long): Long = EPOCH_BASE - epochMillis
}
