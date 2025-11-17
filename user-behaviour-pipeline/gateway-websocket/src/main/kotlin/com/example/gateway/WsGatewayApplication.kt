package com.example.gateway

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication

@SpringBootApplication
class WsGatewayApplication

fun main(args: Array<String>) {
  runApplication<WsGatewayApplication>(*args)
}