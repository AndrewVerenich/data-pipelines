package com.example.retl.sync

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties("retl.sync")
data class SyncProperties(
    val audience: String = "abandoned_checkout",
    val clickhouseUrl: String = "http://localhost:8131",
    val clickhouseUser: String = "admin",
    val clickhousePassword: String = "admin123",
    val emailApiUrl: String = "http://localhost:8098",
    val batchSize: Int = 50,
    val intervalMs: Long = 30_000,
    val initialDelayMs: Long = 20_000,
    val martTable: String = "retl.audience_abandoned_checkout",
)
