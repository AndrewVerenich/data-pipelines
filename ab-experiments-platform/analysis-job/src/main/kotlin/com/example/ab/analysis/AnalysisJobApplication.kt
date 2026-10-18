package com.example.ab.analysis

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.boot.runApplication
import org.springframework.scheduling.annotation.EnableScheduling

@SpringBootApplication
@EnableScheduling
@EnableConfigurationProperties(AnalysisProperties::class)
class AnalysisJobApplication

fun main(args: Array<String>) {
    runApplication<AnalysisJobApplication>(*args)
}
