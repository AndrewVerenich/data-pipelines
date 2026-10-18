package com.example.ab.simulator

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.boot.runApplication
import org.springframework.scheduling.annotation.EnableScheduling

@SpringBootApplication
@EnableScheduling
@EnableConfigurationProperties(SimulatorProperties::class)
class SimulatorApplication

fun main(args: Array<String>) {
    runApplication<SimulatorApplication>(*args)
}
