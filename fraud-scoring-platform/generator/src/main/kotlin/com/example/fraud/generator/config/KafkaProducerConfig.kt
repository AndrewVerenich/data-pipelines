package com.example.fraud.generator.config

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
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
class KafkaProducerConfig(
  @Value("\${spring.kafka.bootstrap-servers}") private val bootstrapServers: String
) {

  @Bean
  fun objectMapper(): ObjectMapper = jacksonObjectMapper()
    .registerModule(JavaTimeModule())
    .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)

  @Bean
  fun producerFactory(objectMapper: ObjectMapper): ProducerFactory<String, Any> {
    val config = mapOf(
      ProducerConfig.BOOTSTRAP_SERVERS_CONFIG to bootstrapServers,
      ProducerConfig.ACKS_CONFIG to "all",
      ProducerConfig.RETRIES_CONFIG to 5,
      ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG to StringSerializer::class.java,
      ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG to JsonSerializer::class.java
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
