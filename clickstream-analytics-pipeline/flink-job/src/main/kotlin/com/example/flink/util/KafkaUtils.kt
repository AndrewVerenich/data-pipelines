package com.example.flink.util

import org.apache.flink.api.common.serialization.SimpleStringSchema
import org.apache.flink.connector.base.DeliveryGuarantee
import org.apache.flink.connector.kafka.sink.KafkaRecordSerializationSchema
import org.apache.flink.connector.kafka.sink.KafkaSink
import org.apache.flink.connector.kafka.source.KafkaSource
import org.apache.flink.connector.kafka.source.enumerator.initializer.OffsetsInitializer

object KafkaUtils {
  fun createSource(
    bootstrap: String,
    topic: String,
    groupId: String,
    earliest: Boolean = true
  ): KafkaSource<String> {
    val builder = KafkaSource.builder<String>()
      .setBootstrapServers(bootstrap)
      .setTopics(topic)
      .setGroupId(groupId)
      .setValueOnlyDeserializer(SimpleStringSchema())
    if (earliest) {
      builder.setStartingOffsets(OffsetsInitializer.earliest())
    }
    return builder.build()
  }

  fun createSink(bootstrap: String, topic: String): KafkaSink<String> =
    KafkaSink.builder<String>()
      .setBootstrapServers(bootstrap)
      .setRecordSerializer(
        KafkaRecordSerializationSchema.builder<String>()
          .setTopic(topic)
          .setValueSerializationSchema(SimpleStringSchema())
          .build()
      )
      .setDeliveryGuarantee(DeliveryGuarantee.AT_LEAST_ONCE)
      .build()
}
