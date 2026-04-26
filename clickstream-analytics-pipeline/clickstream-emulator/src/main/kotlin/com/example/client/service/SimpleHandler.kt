package com.example.client.service

import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import org.springframework.web.socket.TextMessage
import org.springframework.web.socket.WebSocketSession
import org.springframework.web.socket.handler.TextWebSocketHandler

@Component
class SimpleHandler : TextWebSocketHandler() {
  private val logger = LoggerFactory.getLogger(SimpleHandler::class.java)
  override fun handleTextMessage(session: WebSocketSession, message: TextMessage) {
    logger.info("Server response: ${message.payload}")
  }
}