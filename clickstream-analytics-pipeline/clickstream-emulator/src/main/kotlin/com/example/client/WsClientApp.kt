package com.example.client

import com.example.client.service.WebSocketSender
import org.springframework.boot.CommandLineRunner
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication

@SpringBootApplication
class WsClientApp(
  private val sender: WebSocketSender
) : CommandLineRunner {
  override fun run(vararg args: String?) {
    sender.startSending()
  }
}

fun main(args: Array<String>) {
  runApplication<WsClientApp>(*args)
}
