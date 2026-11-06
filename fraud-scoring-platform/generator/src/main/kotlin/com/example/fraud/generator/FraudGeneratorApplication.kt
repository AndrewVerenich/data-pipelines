package com.example.fraud.generator

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication
import org.springframework.scheduling.annotation.EnableScheduling

@SpringBootApplication
@EnableScheduling
class FraudGeneratorApplication

fun main(args: Array<String>) {
  runApplication<FraudGeneratorApplication>(*args)
}
