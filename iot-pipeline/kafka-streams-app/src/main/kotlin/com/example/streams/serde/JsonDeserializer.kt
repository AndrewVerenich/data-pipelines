package com.example.streams.serde

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import org.apache.kafka.common.serialization.Deserializer

class JsonDeserializer<T : Any>(private val type: Class<T>) : Deserializer<T> {
  private val mapper: ObjectMapper = jacksonObjectMapper()

  override fun deserialize(topic: String?, data: ByteArray?): T? {
    return if (data != null) {
      mapper.readValue(data, type)
    } else null
  }
}
