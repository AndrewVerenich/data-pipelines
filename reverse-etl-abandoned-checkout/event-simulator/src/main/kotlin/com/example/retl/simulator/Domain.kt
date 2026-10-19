package com.example.retl.simulator

import com.fasterxml.jackson.annotation.JsonProperty
import java.time.Instant

enum class EventType {
    VIEW_ITEM,
    ADD_TO_CART,
    BEGIN_CHECKOUT,
    PURCHASE,
}

enum class UserScenario {
    ABANDON,
    PURCHASE_LATER,
    BAD_EMAIL,
    UPDATE_CART,
    LATE_EVENT,
}

data class CommerceEvent(
    @JsonProperty("event_id") val eventId: String,
    @JsonProperty("user_id") val userId: String,
    @JsonProperty("event_type") val eventType: String,
    val email: String,
    @JsonProperty("product_id") val productId: String,
    @JsonProperty("cart_value") val cartValue: Double,
    @JsonProperty("session_id") val sessionId: String,
    val ts: Double,
)

data class LiveUser(
    val userId: String,
    val scenario: UserScenario,
    var email: String,
    var cartValue: Double,
    var sessionId: String,
    var productId: String,
    var phase: Int,
    var nextAt: Instant,
    var checkoutAt: Instant? = null,
    var purchased: Boolean = false,
)
