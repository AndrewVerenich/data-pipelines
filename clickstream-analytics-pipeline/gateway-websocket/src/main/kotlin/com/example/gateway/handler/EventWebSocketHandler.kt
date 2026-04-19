package com.example.gateway.handler

import com.fasterxml.jackson.databind.ObjectMapper
import org.slf4j.LoggerFactory
import org.springframework.kafka.core.KafkaTemplate
import org.springframework.stereotype.Component
import org.springframework.web.socket.TextMessage
import org.springframework.web.socket.WebSocketSession
import org.springframework.web.socket.handler.TextWebSocketHandler

@Component
class EventWebSocketHandler(
  private val kafka: KafkaTemplate<String, String>
) : TextWebSocketHandler() {

  private val mapper = ObjectMapper()
  private val topic = "raw_events"
  private val logger = LoggerFactory.getLogger(EventWebSocketHandler::class.java)

  override fun handleTextMessage(session: WebSocketSession, message: TextMessage) {
    val payload = message.payload
    try {
      mapper.readTree(payload) // проверка JSON
      logger.info("Received message: {}", payload)
      kafka.send(topic, payload);
    } catch (_: Exception) {
    }
  }
}