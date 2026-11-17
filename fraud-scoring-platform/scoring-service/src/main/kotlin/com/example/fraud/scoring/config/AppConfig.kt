package com.example.fraud.scoring.config

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.PropertyNamingStrategies
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory
import com.fasterxml.jackson.module.kotlin.KotlinModule
import com.fasterxml.jackson.module.kotlin.readValue
import org.apache.kafka.clients.producer.ProducerConfig
import org.apache.kafka.common.serialization.StringSerializer
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.core.io.ClassPathResource
import org.springframework.kafka.core.DefaultKafkaProducerFactory
import org.springframework.kafka.core.KafkaTemplate
import org.springframework.kafka.core.ProducerFactory
import org.springframework.kafka.support.serializer.JsonSerializer

@JsonIgnoreProperties(ignoreUnknown = true)
data class DecisionThresholds(val block: Double = 0.9, val challenge: Double = 0.7)

@JsonIgnoreProperties(ignoreUnknown = true)
data class FeatureConfig(
  val numericFeatures: List<String> = emptyList(),
  val categoricalFeatures: List<String> = emptyList(),
  val decisionThresholds: DecisionThresholds = DecisionThresholds()
)

@Configuration
class AppConfig(
  @Value("\${spring.kafka.bootstrap-servers}") private val bootstrap: String
) {

  @Bean
  fun featureConfig(): FeatureConfig {
    val mapper = ObjectMapper(YAMLFactory())
      .registerModule(KotlinModule.Builder().build())
      .setPropertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE)
    return ClassPathResource("feature_config.yml").inputStream.use { mapper.readValue(it) }
  }

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
  fun kafkaTemplate(factory: ProducerFactory<String, Any>): KafkaTemplate<String, Any> =
    KafkaTemplate(factory)
}
