package com.example.retl.simulator

import org.slf4j.LoggerFactory
import org.springframework.boot.ApplicationArguments
import org.springframework.boot.ApplicationRunner
import org.springframework.kafka.core.KafkaTemplate
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import java.time.Duration
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong
import kotlin.random.Random

@Component
class EventSimulator(
    private val properties: SimulatorProperties,
    private val kafkaTemplate: KafkaTemplate<String, Any>,
) : ApplicationRunner {
    private val log = LoggerFactory.getLogger(EventSimulator::class.java)
    private val rnd = Random(7)
    private val active = ConcurrentHashMap<String, LiveUser>()
    private val seq = AtomicLong(1)
    @Volatile private var ready = false

    override fun run(args: ApplicationArguments) {
        backfill()
        seedLiveUsers(properties.activeUsers)
        ready = true
        log.info(
            "simulator ready: {} live users, backfill {}h, abandon window {}h",
            active.size,
            properties.backfillHours,
            properties.abandonWindowHours,
        )
    }

    @Scheduled(fixedDelayString = "\${retl.tick-ms:4000}")
    fun tick() {
        if (!ready) return
        val now = Instant.now()
        val finished = mutableListOf<String>()
        active.values.forEach { user ->
            try {
                if (!now.isBefore(user.nextAt)) {
                    advance(user, now)
                }
                if (user.purchased || user.phase >= 99) {
                    finished += user.userId
                }
            } catch (ex: Exception) {
                log.warn("tick failed for {}: {}", user.userId, ex.message)
            }
        }
        finished.forEach { active.remove(it) }
        while (active.size < properties.activeUsers) {
            startUser(now.minusSeconds(rnd.nextLong(30, 600)), live = true)
        }
    }

    private fun backfill() {
        val hours = properties.backfillHours.coerceAtLeast(1)
        val end = Instant.now().minusSeconds(120)
        val start = end.minus(Duration.ofHours(hours.toLong()))
        val journeys = 160
        repeat(journeys) { index ->
            val t0 = start.plusSeconds((index.toLong() * hours * 3600) / journeys)
            replayJourney(t0)
        }
        log.info("backfilled {} journeys over {}h", journeys, hours)
    }

    private fun seedLiveUsers(count: Int) {
        val now = Instant.now()
        repeat(count) {
            startUser(now.minusSeconds(rnd.nextLong(60, 1_200)), live = true)
        }
    }

    private fun startUser(startedAt: Instant, live: Boolean): LiveUser {
        val scenario = pickScenario()
        val userId = "user-${seq.getAndIncrement()}"
        val email = when (scenario) {
            UserScenario.BAD_EMAIL -> "broken-$userId@"
            else -> "$userId@example.com"
        }
        val user = LiveUser(
            userId = userId,
            scenario = scenario,
            email = email,
            cartValue = 20.0 + rnd.nextDouble() * 180.0,
            sessionId = "sess-${seq.getAndIncrement()}",
            productId = "sku-${rnd.nextInt(1, 80)}",
            phase = 0,
            nextAt = startedAt,
        )
        if (live) {
            active[userId] = user
        } else {
            replayLivePath(user, startedAt)
        }
        return user
    }

    private fun replayJourney(startedAt: Instant) {
        val user = startUser(startedAt, live = false)
        // already fully replayed in startUser(live=false)
        user.phase = 99
    }

    private fun replayLivePath(user: LiveUser, startedAt: Instant) {
        var t = startedAt
        publish(user, EventType.VIEW_ITEM, t)
        t = t.plusSeconds(20 + rnd.nextLong(40))
        publish(user, EventType.ADD_TO_CART, t)
        t = t.plusSeconds(30 + rnd.nextLong(90))
        publish(user, EventType.BEGIN_CHECKOUT, t)
        user.checkoutAt = t

        when (user.scenario) {
            UserScenario.PURCHASE_LATER -> {
                t = t.plusSeconds(120 + rnd.nextLong(600))
                publish(user, EventType.PURCHASE, t)
                user.purchased = true
            }
            UserScenario.UPDATE_CART -> {
                t = t.plusSeconds(180 + rnd.nextLong(300))
                user.cartValue = (user.cartValue + 15 + rnd.nextDouble() * 40).coerceAtMost(400.0)
                user.productId = "sku-${rnd.nextInt(1, 80)}"
                publish(user, EventType.ADD_TO_CART, t)
                t = t.plusSeconds(60)
                publish(user, EventType.BEGIN_CHECKOUT, t)
                user.checkoutAt = t
            }
            UserScenario.LATE_EVENT -> {
                // checkout already published; a late view arrives after a gap
                t = t.plusSeconds(2_400)
                publish(user, EventType.VIEW_ITEM, t)
            }
            UserScenario.ABANDON, UserScenario.BAD_EMAIL -> {
                // leave abandoned
            }
        }
        user.phase = 99
    }

    private fun advance(user: LiveUser, now: Instant) {
        when (user.phase) {
            0 -> {
                publish(user, EventType.VIEW_ITEM, now)
                user.phase = 1
                user.nextAt = now.plusSeconds(15 + rnd.nextLong(45))
            }
            1 -> {
                publish(user, EventType.ADD_TO_CART, now)
                user.phase = 2
                user.nextAt = now.plusSeconds(20 + rnd.nextLong(60))
            }
            2 -> {
                publish(user, EventType.BEGIN_CHECKOUT, now)
                user.checkoutAt = now
                user.phase = when (user.scenario) {
                    UserScenario.PURCHASE_LATER -> 3
                    UserScenario.UPDATE_CART -> 4
                    UserScenario.LATE_EVENT -> 5
                    else -> 10 // sit in abandoned state
                }
                user.nextAt = now.plusSeconds(
                    when (user.scenario) {
                        UserScenario.PURCHASE_LATER -> 90 + rnd.nextLong(180)
                        UserScenario.UPDATE_CART -> 60 + rnd.nextLong(120)
                        UserScenario.LATE_EVENT -> 180 + rnd.nextLong(120)
                        else -> 300 + rnd.nextLong(600)
                    },
                )
            }
            3 -> {
                publish(user, EventType.PURCHASE, now)
                user.purchased = true
                user.phase = 99
            }
            4 -> {
                user.cartValue = (user.cartValue + 10 + rnd.nextDouble() * 30).coerceAtMost(400.0)
                user.email = if (rnd.nextBoolean()) user.email else "alt-${user.userId}@example.com"
                publish(user, EventType.ADD_TO_CART, now)
                user.phase = 2
                user.nextAt = now.plusSeconds(30)
            }
            5 -> {
                publish(user, EventType.VIEW_ITEM, now.minusSeconds(90))
                user.phase = 10
                user.nextAt = now.plusSeconds(400)
            }
            10 -> {
                // recycle abandoned user into a new journey eventually
                user.phase = 99
            }
            else -> user.phase = 99
        }
    }

    private fun publish(user: LiveUser, type: EventType, at: Instant) {
        val event = CommerceEvent(
            eventId = "evt-${seq.getAndIncrement()}",
            userId = user.userId,
            eventType = type.name,
            email = user.email,
            productId = user.productId,
            cartValue = Math.round(user.cartValue * 100.0) / 100.0,
            sessionId = user.sessionId,
            ts = at.epochSecond + at.nano / 1_000_000_000.0,
        )
        kafkaTemplate.send(properties.eventsTopic, user.userId, event)
            .get(5, TimeUnit.SECONDS)
    }

    private fun pickScenario(): UserScenario {
        return when (rnd.nextInt(100)) {
            in 0..54 -> UserScenario.ABANDON
            in 55..69 -> UserScenario.PURCHASE_LATER
            in 70..79 -> UserScenario.UPDATE_CART
            in 80..89 -> UserScenario.LATE_EVENT
            else -> UserScenario.BAD_EMAIL
        }
    }
}
