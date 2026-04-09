package com.example.streams.stream

import com.example.streams.cdc.RoomConfigCdcParser
import com.example.streams.topology.SmartHomeTopology
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.kotlinModule
import org.apache.kafka.common.serialization.Serdes
import org.apache.kafka.streams.StreamsBuilder
import org.apache.kafka.streams.StreamsConfig
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.kafka.annotation.KafkaStreamsDefaultConfiguration
import org.springframework.kafka.config.KafkaStreamsConfiguration
import org.springframework.kafka.config.KafkaStreamsInfrastructureCustomizer
import org.springframework.kafka.config.StreamsBuilderFactoryBeanConfigurer

@Configuration
class KafkaStreamsConfig {

  @Bean
  fun objectMapper(): ObjectMapper = ObjectMapper().registerModule(kotlinModule())

  @Bean(name = [KafkaStreamsDefaultConfiguration.DEFAULT_STREAMS_CONFIG_BEAN_NAME])
  fun kafkaStreamsConfiguration(): KafkaStreamsConfiguration {
    val props: MutableMap<String, Any> = HashMap()
    props[StreamsConfig.APPLICATION_ID_CONFIG] = "smart-home-streams-v1"
    props[StreamsConfig.BOOTSTRAP_SERVERS_CONFIG] =
      System.getenv("SPRING_KAFKA_STREAMS_BOOTSTRAP_SERVERS") ?: "localhost:9092"
    props[StreamsConfig.DEFAULT_KEY_SERDE_CLASS_CONFIG] = Serdes.String()::class.java.name
    props[StreamsConfig.DEFAULT_VALUE_SERDE_CLASS_CONFIG] = Serdes.String()::class.java.name
    props[StreamsConfig.PROCESSING_GUARANTEE_CONFIG] = StreamsConfig.AT_LEAST_ONCE
    props[StreamsConfig.STATE_DIR_CONFIG] =
      (System.getenv("KAFKA_STREAMS_STATE_DIR") ?: (System.getProperty("java.io.tmpdir") + "/kafka-streams-state"))
    return KafkaStreamsConfiguration(props)
  }

  @Bean
  fun streamsCustomizer(
    mapper: ObjectMapper,
    roomConfigCdcParser: RoomConfigCdcParser,
  ): KafkaStreamsInfrastructureCustomizer =
    object : KafkaStreamsInfrastructureCustomizer {
      override fun configureBuilder(builder: StreamsBuilder) {
        SmartHomeTopology.build(builder, mapper, roomConfigCdcParser)
      }
    }

  @Bean
  fun streamsBuilderFactoryBeanConfigurer(
    streamsCustomizer: KafkaStreamsInfrastructureCustomizer,
  ): StreamsBuilderFactoryBeanConfigurer =
    StreamsBuilderFactoryBeanConfigurer { fb ->
      fb.setInfrastructureCustomizer(streamsCustomizer)
    }
}
