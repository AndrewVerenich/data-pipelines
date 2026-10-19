package com.example.retl.sync

import com.fasterxml.jackson.databind.ObjectMapper
import org.springframework.stereotype.Component
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration

data class EmailDeliveryResult(
    val httpStatus: Int,
    val body: String,
)

@Component
class EmailClient(
    private val properties: SyncProperties,
    private val objectMapper: ObjectMapper,
) {
    private val http = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(5))
        .build()

    fun deliver(change: SyncChange, audience: String, idempotencyKey: String): EmailDeliveryResult {
        val payload = mapOf(
            "userId" to change.userId,
            "email" to change.email,
            "audience" to audience,
            "action" to change.action.name.lowercase(),
            "version" to change.version,
            "cartValue" to change.cartValue,
            "idempotencyKey" to idempotencyKey,
        )
        val request = HttpRequest.newBuilder()
            .uri(URI.create("${properties.emailApiUrl.trimEnd('/')}/v1/subscriptions"))
            .timeout(Duration.ofSeconds(15))
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(payload)))
            .build()
        val response = http.send(request, HttpResponse.BodyHandlers.ofString())
        return EmailDeliveryResult(response.statusCode(), response.body())
    }
}
