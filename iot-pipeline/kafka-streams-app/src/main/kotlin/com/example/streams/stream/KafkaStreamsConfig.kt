package com.example.streams.stream

import org.apache.kafka.common.serialization.Serdes
import org.apache.kafka.streams.StreamsConfig.APPLICATION_ID_CONFIG
import org.apache.kafka.streams.StreamsConfig.BOOTSTRAP_SERVERS_CONFIG
import org.apache.kafka.streams.StreamsConfig.DEFAULT_KEY_SERDE_CLASS_CONFIG
import org.apache.kafka.streams.StreamsConfig.DEFAULT_VALUE_SERDE_CLASS_CONFIG
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.kafka.annotation.KafkaStreamsDefaultConfiguration
import org.springframework.kafka.config.KafkaStreamsConfiguration


@Configuration
class KafkaStreamsConfig {
  @Bean(name = [KafkaStreamsDefaultConfiguration.DEFAULT_STREAMS_CONFIG_BEAN_NAME])
  fun kafkaStreamsConfiguration(
  ): KafkaStreamsConfiguration {
    val props: MutableMap<String, Any> = HashMap()
    props[APPLICATION_ID_CONFIG] = "iot-streams-app"
    props[BOOTSTRAP_SERVERS_CONFIG] = "kafka:9092"
    props[DEFAULT_KEY_SERDE_CLASS_CONFIG] = Serdes.String().javaClass.name
    props[DEFAULT_VALUE_SERDE_CLASS_CONFIG] = Serdes.String().javaClass.name

    return KafkaStreamsConfiguration(props)
  }
}
