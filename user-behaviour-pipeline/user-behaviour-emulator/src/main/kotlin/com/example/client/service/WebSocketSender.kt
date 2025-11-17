package com.example.client.service

import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import org.springframework.web.socket.TextMessage
import org.springframework.web.socket.WebSocketSession
import org.springframework.web.socket.client.standard.StandardWebSocketClient
import java.util.*

@Component
class WebSocketSender {
  private val random = Random()
  private val events = listOf("click", "view", "purchase")
  private val pages = listOf("/home", "/product", "/checkout")
  private val logger = LoggerFactory.getLogger(WebSocketSender::class.java)

  fun startSending() {
    val client = StandardWebSocketClient()
    val session: WebSocketSession = client
      .execute(SimpleHandler(), System.getenv("WS_URL") ?: "ws://localhost:8080/ws/events")
      .get()


    while (true) {
      val event = """{
          "userId":"user_${random.nextInt(1000)}",
          "event":"${events.random()}",
          "page":"${pages.random()}",
          "timestamp":${System.currentTimeMillis()}
        }"""
      session.sendMessage(TextMessage(event))
      logger.info("Sent: $event")
      Thread.sleep((System.getenv("SEND_INTERVAL_MS") ?: "1000").toLong())
    }
  }
}