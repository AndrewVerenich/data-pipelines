package com.example.fraud.scoring

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication

@SpringBootApplication
class ScoringServiceApplication

fun main(args: Array<String>) {
  runApplication<ScoringServiceApplication>(*args)
}
