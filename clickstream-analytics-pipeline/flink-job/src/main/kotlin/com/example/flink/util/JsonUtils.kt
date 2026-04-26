package com.example.flink.util

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue

object JsonUtils {
  val mapper = jacksonObjectMapper()

  fun toJson(obj: Any): String = mapper.writeValueAsString(obj)

  inline fun <reified T> fromJson(json: String): T = mapper.readValue(json)

  inline fun <reified T> fromJsonOrNull(json: String): T? = try {
    mapper.readValue<T>(json)
  } catch (_: Exception) {
    null
  }
}
