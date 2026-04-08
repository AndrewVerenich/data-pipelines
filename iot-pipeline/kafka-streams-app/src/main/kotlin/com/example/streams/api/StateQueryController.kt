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
  fun lastHvac(@PathVariable roomId: String): LastHvacResponse {
    val raw = queryStore(roomId)
      ?: return LastHvacResponse(roomId = roomId, action = null, reason = null)
    val node: JsonNode = mapper.readTree(raw)
    return LastHvacResponse(
      roomId = node.get("roomId").asText(),
      action = node.get("action").asText(),
      reason = node.get("reason")?.asText(),
    )
  }

  @GetMapping("/rooms")
  fun allHvac(): List<LastHvacResponse> {
    val store = readOnlyStore() ?: return emptyList()
    val out = mutableListOf<LastHvacResponse>()
    store.all().use { iter ->
      while (iter.hasNext()) {
        val e = iter.next()
        val node = mapper.readTree(e.value)
        out.add(
          LastHvacResponse(
            roomId = node.get("roomId").asText(),
            action = node.get("action").asText(),
            reason = node.get("reason")?.asText(),
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

data class LastHvacResponse(
  val roomId: String,
  val action: String?,
  val reason: String?,
)