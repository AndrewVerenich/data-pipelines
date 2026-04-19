package com.example.client.service

import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import org.springframework.web.socket.TextMessage
import org.springframework.web.socket.WebSocketSession
import org.springframework.web.socket.client.standard.StandardWebSocketClient
import java.util.concurrent.ThreadLocalRandom

/**
 * Emulates a realistic e-commerce clickstream by driving a pool of virtual users through
 * a simple state machine:
 *
 *   LANDED -> BROWSING -> CLICKED -> IN_CART -> AT_CHECKOUT -> PURCHASED
 *
 * Each transition has a probability so the resulting stream has realistic drop-off between
 * funnel steps. A small number of users are flagged as fraudsters and periodically fire
 * bursts of clicks to exercise the fraud-detection path.
 */
@Component
class WebSocketSender {

  private enum class UserState { LANDED, BROWSING, CLICKED, IN_CART, AT_CHECKOUT }

  private data class UserContext(
    var state: UserState = UserState.LANDED,
    var productId: String? = null,
    var category: String? = null,
    var lastPage: String = "/home"
  )

  private val log = LoggerFactory.getLogger(WebSocketSender::class.java)
  private val rng = ThreadLocalRandom.current()

  private val totalUsers = 100
  private val fraudsterIds: Set<String> = (1..5).map { "user_$it" }.toSet()
  private val userCtx: MutableMap<String, UserContext> =
    (1..totalUsers).associate { "user_$it" to UserContext() }.toMutableMap()

  private val categories = listOf("electronics", "clothing", "books", "home", "sports")
  private val products = (1..50).map { "prod_$it" }
  private val categoryByProduct = products.associateWith { categories[it.hashCode().rem(categories.size).let { m -> if (m < 0) m + categories.size else m }] }
  private val searchQueries = listOf(
    "laptop", "running shoes", "headphones", "novel", "kitchen knife",
    "yoga mat", "bluetooth speaker", "backpack", "coffee maker", "sunglasses"
  )

  fun startSending() {
    val client = StandardWebSocketClient()
    val wsUrl = System.getenv("WS_URL") ?: "ws://localhost:8080/ws/events"
    val session: WebSocketSession = client.execute(SimpleHandler(), wsUrl).get()
    val interval = (System.getenv("SEND_INTERVAL_MS") ?: "200").toLong()

    log.info("WebSocketSender started: wsUrl={}, interval={}ms, users={}, fraudsters={}",
      wsUrl, interval, totalUsers, fraudsterIds.size)

    var tick = 0L
    while (true) {
      tick++

      if (tick % 150 == 0L) {
        sendFraudBurst(session)
      } else {
        val userId = pickUserId()
        val events = advanceUser(userId)
        events.forEach { json ->
          session.sendMessage(TextMessage(json))
        }
      }

      Thread.sleep(interval)
    }
  }

  private fun pickUserId(): String = "user_${rng.nextInt(totalUsers) + 1}"

  /**
   * Advances a single user by one step through their state machine, returning 1..2 JSON
   * payloads ready to send. Returning up to two keeps bursts realistic
   * (e.g. click often produces a follow-up page view).
   */
  private fun advanceUser(userId: String): List<String> {
    val ctx = userCtx.getValue(userId)
    val now = System.currentTimeMillis()

    // small chance of a search event regardless of state
    if (rng.nextDouble() < 0.03) {
      val query = searchQueries.random()
      return listOf(
        buildJson(
          userId = userId,
          eventType = "search",
          page = "/search",
          searchQuery = query,
          referrer = ctx.lastPage,
          timestamp = now
        )
      )
    }

    return when (ctx.state) {
      UserState.LANDED -> {
        val page = if (rng.nextBoolean()) "/home" else "/catalog"
        val prev = ctx.lastPage
        ctx.lastPage = page
        ctx.state = UserState.BROWSING
        listOf(buildJson(userId, "page_view", page, referrer = prev, timestamp = now))
      }

      UserState.BROWSING -> {
        val roll = rng.nextDouble()
        when {
          roll < 0.35 -> {
            val product = products.random()
            val page = "/product/$product"
            val prev = ctx.lastPage
            ctx.productId = product
            ctx.category = categoryByProduct[product]
            ctx.lastPage = page
            ctx.state = UserState.CLICKED
            listOf(
              buildJson(
                userId, "page_view", page,
                productId = product, category = ctx.category,
                referrer = prev, timestamp = now
              ),
              buildJson(
                userId, "click", page,
                productId = product, category = ctx.category,
                referrer = prev, timestamp = now + 50
              )
            )
          }
          roll < 0.80 -> {
            val page = listOf("/catalog", "/home", "/search").random()
            val prev = ctx.lastPage
            ctx.lastPage = page
            listOf(buildJson(userId, "page_view", page, referrer = prev, timestamp = now))
          }
          else -> {
            resetUser(ctx, "/home")
            listOf(buildJson(userId, "page_view", "/home", referrer = ctx.lastPage, timestamp = now))
          }
        }
      }

      UserState.CLICKED -> {
        val product = ctx.productId ?: products.random()
        val roll = rng.nextDouble()
        when {
          roll < 0.35 -> {
            val page = "/cart"
            val prev = ctx.lastPage
            val price = priceFor(product)
            ctx.lastPage = page
            ctx.state = UserState.IN_CART
            listOf(
              buildJson(
                userId, "add_to_cart", page,
                productId = product, category = ctx.category,
                price = price, quantity = 1,
                referrer = prev, timestamp = now
              )
            )
          }
          roll < 0.70 -> {
            val page = "/catalog"
            val prev = ctx.lastPage
            ctx.lastPage = page
            ctx.state = UserState.BROWSING
            listOf(buildJson(userId, "page_view", page, referrer = prev, timestamp = now))
          }
          else -> {
            resetUser(ctx, "/home")
            listOf(buildJson(userId, "page_view", "/home", referrer = ctx.lastPage, timestamp = now))
          }
        }
      }

      UserState.IN_CART -> {
        val product = ctx.productId ?: products.random()
        val roll = rng.nextDouble()
        when {
          roll < 0.45 -> {
            val page = "/checkout"
            val prev = ctx.lastPage
            ctx.lastPage = page
            ctx.state = UserState.AT_CHECKOUT
            listOf(
              buildJson(
                userId, "checkout_start", page,
                productId = product, category = ctx.category,
                price = priceFor(product), quantity = 1,
                referrer = prev, timestamp = now
              )
            )
          }
          roll < 0.60 -> {
            val page = "/cart"
            val prev = ctx.lastPage
            ctx.state = UserState.CLICKED
            ctx.lastPage = "/product/$product"
            listOf(
              buildJson(
                userId, "remove_from_cart", page,
                productId = product, category = ctx.category,
                referrer = prev, timestamp = now
              )
            )
          }
          else -> {
            resetUser(ctx, "/home")
            listOf(buildJson(userId, "page_view", "/home", referrer = ctx.lastPage, timestamp = now))
          }
        }
      }

      UserState.AT_CHECKOUT -> {
        val product = ctx.productId ?: products.random()
        val roll = rng.nextDouble()
        if (roll < 0.65) {
          val page = "/checkout/confirm"
          val prev = ctx.lastPage
          val price = priceFor(product)
          resetUser(ctx, "/home")
          listOf(
            buildJson(
              userId, "purchase", page,
              productId = product, category = categoryByProduct[product],
              price = price, quantity = 1,
              referrer = prev, timestamp = now
            )
          )
        } else {
          resetUser(ctx, "/home")
          listOf(buildJson(userId, "page_view", "/home", referrer = "/checkout", timestamp = now))
        }
      }
    }
  }

  /**
   * Emits a rapid burst of clicks from a random fraudster to trigger the broadcast
   * fraud rules (e.g. MAX_CLICKS_PER_WINDOW).
   */
  private fun sendFraudBurst(session: WebSocketSession) {
    val userId = fraudsterIds.random()
    val burstSize = 25 + rng.nextInt(10)
    val product = products.random()
    val page = "/product/$product"
    val now = System.currentTimeMillis()
    log.info("Emitting fraud burst: user={}, size={}", userId, burstSize)
    for (i in 0 until burstSize) {
      val json = buildJson(
        userId = userId,
        eventType = "click",
        page = page,
        productId = product,
        category = categoryByProduct[product],
        referrer = "/catalog",
        timestamp = now + i * 50L
      )
      session.sendMessage(TextMessage(json))
    }
  }

  private fun resetUser(ctx: UserContext, landingPage: String) {
    ctx.state = UserState.LANDED
    ctx.productId = null
    ctx.category = null
    ctx.lastPage = landingPage
  }

  private fun priceFor(productId: String): Double {
    val seed = productId.hashCode().rem(495).let { if (it < 0) it + 495 else it }
    return 5.0 + seed
  }

  private fun buildJson(
    userId: String,
    eventType: String,
    page: String,
    productId: String? = null,
    category: String? = null,
    price: Double? = null,
    quantity: Int? = null,
    searchQuery: String? = null,
    referrer: String? = null,
    timestamp: Long
  ): String {
    val sb = StringBuilder(256)
    sb.append('{')
    sb.append("\"userId\":\"").append(userId).append('"')
    sb.append(",\"eventType\":\"").append(eventType).append('"')
    sb.append(",\"page\":\"").append(page).append('"')
    if (productId != null) sb.append(",\"productId\":\"").append(productId).append('"')
    if (category != null) sb.append(",\"category\":\"").append(category).append('"')
    if (price != null) sb.append(",\"price\":").append(String.format("%.2f", price))
    if (quantity != null) sb.append(",\"quantity\":").append(quantity)
    if (searchQuery != null) sb.append(",\"searchQuery\":\"").append(searchQuery).append('"')
    if (referrer != null) sb.append(",\"referrer\":\"").append(referrer).append('"')
    sb.append(",\"timestamp\":").append(timestamp)
    sb.append('}')
    return sb.toString()
  }
}
