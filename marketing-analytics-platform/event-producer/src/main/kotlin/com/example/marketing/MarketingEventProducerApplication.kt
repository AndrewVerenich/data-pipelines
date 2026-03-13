package com.example.marketing

import org.springframework.boot.SpringApplication
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.scheduling.annotation.EnableScheduling

@SpringBootApplication
@EnableScheduling
class MarketingEventProducerApplication

fun main(args: Array<String>) {
  SpringApplication.run(MarketingEventProducerApplication::class.java, *args)
}
