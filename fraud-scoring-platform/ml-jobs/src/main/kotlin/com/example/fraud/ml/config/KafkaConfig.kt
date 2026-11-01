package com.example.fraud.ml.config

import com.fasterxml.jackson.databind.ObjectMapper
import org.apache.kafka.clients.producer.ProducerConfig
import org.apache.kafka.common.serialization.StringSerializer
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.kafka.core.DefaultKafkaProducerFactory
import org.springframework.kafka.core.KafkaTemplate
import org.springframework.kafka.core.ProducerFactory
import org.springframework.kafka.support.serializer.JsonSerializer

@Configuration
class KafkaConfig(
  @Value("\${spring.kafka.bootstrap-servers}") private val bootstrap: String
) {
  @Bean
  fun objectMapper(): ObjectMapper = com.fasterxml.jackson.module.kotlin.jacksonObjectMapper()

  @Bean
  fun producerFactory(objectMapper: ObjectMapper): ProducerFactory<String, Any> {
    val config = mapOf(
      ProducerConfig.BOOTSTRAP_SERVERS_CONFIG to bootstrap,
      ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG to StringSerializer::class.java,
      ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG to JsonSerializer::class.java,
      ProducerConfig.ACKS_CONFIG to "all"
    )
    val factory = DefaultKafkaProducerFactory<String, Any>(config)
    val serializer = JsonSerializer<Any>(objectMapper)
    serializer.isAddTypeInfo = false
    factory.setValueSerializer(serializer)
    return factory
  }

  @Bean
  fun kafkaTemplate(producerFactory: ProducerFactory<String, Any>): KafkaTemplate<String, Any> =
    KafkaTemplate(producerFactory)
}
