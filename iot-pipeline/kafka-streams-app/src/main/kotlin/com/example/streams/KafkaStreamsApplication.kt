package com.example.streams

import org.springframework.boot.SpringApplication
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.kafka.annotation.EnableKafka
import org.springframework.kafka.annotation.EnableKafkaStreams

@SpringBootApplication
@EnableKafka
@EnableKafkaStreams
class KafkaStreamsApplication

fun main(args: Array<String>) {
  SpringApplication.run(KafkaStreamsApplication::class.java, *args)
}
