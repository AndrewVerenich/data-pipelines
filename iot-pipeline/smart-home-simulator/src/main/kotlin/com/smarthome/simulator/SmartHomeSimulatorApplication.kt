package com.smarthome.simulator

import com.smarthome.simulator.config.SmartHomeProperties
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.boot.runApplication
import org.springframework.kafka.annotation.EnableKafka
import org.springframework.scheduling.annotation.EnableScheduling

@SpringBootApplication
@EnableScheduling
@EnableKafka
@EnableConfigurationProperties(SmartHomeProperties::class)
class SmartHomeSimulatorApplication

fun main(args: Array<String>) {
  runApplication<SmartHomeSimulatorApplication>(*args)
}
