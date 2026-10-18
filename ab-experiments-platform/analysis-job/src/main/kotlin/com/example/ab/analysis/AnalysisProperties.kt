package com.example.ab.analysis

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties("ab")
data class AnalysisProperties(
    val clickhouseUrl: String,
    val clickhouseUser: String,
    val clickhousePassword: String,
    val intervalMs: Long = 60_000,
    val initialDelayMs: Long = 5_000,
)
