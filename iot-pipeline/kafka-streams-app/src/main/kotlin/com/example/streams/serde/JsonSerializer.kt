package com.example.streams.serde

import com.fasterxml.jackson.databind.ObjectMapper
import org.apache.kafka.common.serialization.Serializer

class JsonSerializer<T> : Serializer<T> {
  private val mapper = ObjectMapper()

  override fun serialize(topic: String?, data: T): ByteArray {
    return mapper.writeValueAsBytes(data)
  }
}
