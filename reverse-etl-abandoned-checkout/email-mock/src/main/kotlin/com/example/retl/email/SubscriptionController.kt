package com.example.retl.email

import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.util.concurrent.ConcurrentHashMap
import kotlin.random.Random

data class SubscriptionRequest(
    val userId: String,
    val email: String,
    val audience: String,
    val action: String,
    val version: String,
    val cartValue: Double? = null,
    val idempotencyKey: String,
)

data class SubscriptionResponse(
    val status: String,
    val subscriptionId: String? = null,
    val message: String? = null,
)

@RestController
@RequestMapping("/v1")
class SubscriptionController(
    private val properties: EmailMockProperties,
    private val jdbc: JdbcTemplate,
) {
    private val log = LoggerFactory.getLogger(SubscriptionController::class.java)
    private val seenKeys = ConcurrentHashMap.newKeySet<String>()
    private val rnd = Random(11)

    @PostMapping("/subscriptions")
    fun upsert(@RequestBody body: SubscriptionRequest): ResponseEntity<SubscriptionResponse> {
        if (body.idempotencyKey in seenKeys) {
            return ResponseEntity.ok(
                SubscriptionResponse(status = "duplicate", subscriptionId = "sub-${body.userId}", message = "idempotent replay"),
            )
        }
        if (!isValidEmail(body.email)) {
            recordCall(body, 422, "rejected")
            return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY).body(
                SubscriptionResponse(status = "rejected", message = "invalid email"),
            )
        }
        if (rnd.nextDouble() < properties.rateLimitProbability) {
            recordCall(body, 429, "rate_limited")
            return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS).body(
                SubscriptionResponse(status = "rate_limited", message = "retry later"),
            )
        }

        seenKeys += body.idempotencyKey
        val subId = "sub-${body.userId}-${body.audience}"
        recordCall(body, 200, "accepted")
        log.info("{} {} user={} version={}", body.action, body.audience, body.userId, body.version)
        return ResponseEntity.ok(SubscriptionResponse(status = "accepted", subscriptionId = subId))
    }

    private fun isValidEmail(email: String): Boolean {
        val at = email.indexOf('@')
        return at > 0 && at < email.length - 1 && email.substring(at + 1).contains('.')
    }

    private fun recordCall(body: SubscriptionRequest, httpStatus: Int, outcome: String) {
        jdbc.update(
            """
            INSERT INTO email_api_calls (user_id, audience, action, version, idempotency_key, http_status, outcome)
            VALUES (?, ?, ?, ?, ?, ?, ?)
            """.trimIndent(),
            body.userId,
            body.audience,
            body.action,
            body.version,
            body.idempotencyKey,
            httpStatus,
            outcome,
        )
    }
}
