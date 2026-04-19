package com.example.gateway.config

import org.apache.kafka.clients.producer.ProducerConfig
import org.apache.kafka.common.serialization.StringSerializer
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.kafka.core.DefaultKafkaProducerFactory
import org.springframework.kafka.core.KafkaTemplate
import org.springframework.kafka.core.ProducerFactory

@Configuration
class KafkaConfig {
  @Bean
  fun producerFactory(): ProducerFactory<String?, String?> {
    val bootstrap = System.getenv().getOrDefault("KAFKA_BOOTSTRAP", "localhost:9092")
    val props: MutableMap<String?, Any?> = HashMap<String?, Any?>()
    props.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrap)
    props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer::class.java)
    props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer::class.java)
    props.put(ProducerConfig.ACKS_CONFIG, "1")
    return DefaultKafkaProducerFactory<String?, String?>(props)
  }

  @Bean
  fun kafkaTemplate(pf: ProducerFactory<String?, String?>): KafkaTemplate<String?, String?> {
    return KafkaTemplate<String?, String?>(pf)
  }
}