package com.example.streams.serde

import com.fasterxml.jackson.databind.ObjectMapper
import org.apache.kafka.common.serialization.Deserializer
import org.apache.kafka.common.serialization.Serde
import org.apache.kafka.common.serialization.Serdes
import org.apache.kafka.common.serialization.Serializer

fun <T> jacksonSerde(mapper: ObjectMapper, clazz: Class<T>): Serde<T> {
  val ser = Serializer<T> { _, data ->
    if (data == null) null
    else mapper.writeValueAsBytes(data)
  }
  val de = Deserializer<T> { _, bytes ->
    if (bytes == null) null
    else mapper.readValue(bytes, clazz)
  }
  return Serdes.serdeFrom(ser, de)
}
