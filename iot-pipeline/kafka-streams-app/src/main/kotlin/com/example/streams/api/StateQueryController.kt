package com.example.streams.api

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.example.streams.enums.StreamOutputAction
import org.apache.kafka.streams.StoreQueryParameters
import org.apache.kafka.streams.state.QueryableStoreTypes
import org.apache.kafka.streams.state.ReadOnlyKeyValueStore
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.kafka.annotation.KafkaStreamsDefaultConfiguration
import org.springframework.kafka.config.StreamsBuilderFactoryBean
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/state")
class StateQueryController(
  @Qualifier(KafkaStreamsDefaultConfiguration.DEFAULT_STREAMS_BUILDER_BEAN_NAME)
  private val streamsBuilderFactoryBean: StreamsBuilderFactoryBean,
  private val mapper: ObjectMapper,
) {

  @GetMapping("/rooms/{roomId}/hvac")
  fun lastHvac(@PathVariable roomId: String): LastHvacResponse {
    val raw = queryStore(roomId)
      ?: return LastHvacResponse(roomId = roomId, action = null, reason = null)
    val node: JsonNode = mapper.readTree(raw)
    return LastHvacResponse(
      roomId = node.get("roomId").asText(),
      action = parseStreamAction(node),
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
            action = parseStreamAction(node),
            reason = node.get("reason")?.asText(),
          ),
        )
      }
    }
    return out
  }

  private fun parseStreamAction(node: JsonNode): StreamOutputAction? {
    val n = node.get("action") ?: return null
    return try {
      StreamOutputAction.fromWire(n.asText())
    } catch (_: IllegalArgumentException) {
      null
    }
  }

  private fun queryStore(roomId: String): String? = readOnlyStore()?.get(roomId)

  private fun readOnlyStore(): ReadOnlyKeyValueStore<String, String>? {
    val kafkaStreams = streamsBuilderFactoryBean.kafkaStreams ?: return null
    return try {
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
}

data class LastHvacResponse(
  val roomId: String,
  val action: StreamOutputAction?,
  val reason: String?,
)
