package com.example.streams.api

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import org.apache.kafka.streams.KafkaStreams
import org.apache.kafka.streams.StoreQueryParameters
import org.apache.kafka.streams.state.QueryableStoreTypes
import org.apache.kafka.streams.state.ReadOnlyKeyValueStore
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/state")
class StateQueryController(
  private val kafkaStreams: KafkaStreams,
  private val mapper: ObjectMapper,
) {

  @GetMapping("/rooms/{roomId}/hvac")
  fun lastHvac(@PathVariable roomId: String): Map<String, Any?> {
    val raw = queryStore(roomId) ?: return mapOf("room_id" to roomId, "last_command" to null)
    val node: JsonNode = mapper.readTree(raw)
    return mapOf(
      "room_id" to node.get("room_id").asText(),
      "action" to node.get("action").asText(),
      "reason" to node.get("reason")?.asText(),
    )
  }

  @GetMapping("/rooms")
  fun allHvac(): List<Map<String, Any?>> {
    val store = readOnlyStore() ?: return emptyList()
    val out = mutableListOf<Map<String, Any?>>()
    store.all().use { iter ->
      while (iter.hasNext()) {
        val e = iter.next()
        val node = mapper.readTree(e.value)
        out.add(
          mapOf(
            "room_id" to node.get("room_id").asText(),
            "action" to node.get("action").asText(),
            "reason" to node.get("reason")?.asText(),
          ),
        )
      }
    }
    return out
  }

  private fun queryStore(roomId: String): String? = readOnlyStore()?.get(roomId)

  private fun readOnlyStore(): ReadOnlyKeyValueStore<String, String>? =
    try {
      kafkaStreams.store(
        StoreQueryParameters.fromNameAndType(
          "last-hvac-store",
          QueryableStoreTypes.keyValueStore(),
        ),
      )
    } catch (_: Exception) {
      null
    }
}
