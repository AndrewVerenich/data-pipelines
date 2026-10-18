package com.example.logistics.simulator

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
class TripSimulator(
    private val properties: SimulatorProperties,
    private val kafkaTemplate: KafkaTemplate<String, Any>,
) : ApplicationRunner {
    private val log = LoggerFactory.getLogger(TripSimulator::class.java)
    private val rnd = Random(42)
    private val active = ConcurrentHashMap<String, ActiveTrip>()
    private val seq = AtomicLong(1)
    @Volatile private var ready = false

    override fun run(args: ApplicationArguments) {
        backfill()
        seedLiveTrips(properties.activeTrips)
        ready = true
        log.info("simulator ready: {} live trips, backfill {}h", active.size, properties.backfillHours)
    }

    @Scheduled(fixedDelayString = "\${logistics.tick-ms:5000}")
    fun tick() {
        if (!ready) return
        val now = Instant.now()
        val finished = mutableListOf<String>()
        active.values.forEach { trip ->
            try {
                advance(trip, now)
                publishPing(trip, now)
                if (trip.phase == TripPhase.DELIVERED) {
                    finished += trip.tripId
                }
            } catch (ex: Exception) {
                log.warn("tick failed for {}: {}", trip.tripId, ex.message)
            }
        }
        finished.forEach { active.remove(it) }
        while (active.size < properties.activeTrips) {
            startTrip(now, live = true)
        }
    }

    private fun backfill() {
        val hours = properties.backfillHours.coerceAtLeast(1)
        val end = Instant.now().minusSeconds(60)
        val start = end.minus(Duration.ofHours(hours.toLong()))
        val trips = 80
        repeat(trips) { index ->
            val assignedAt = start.plusSeconds((index.toLong() * hours * 3600) / trips)
            replayCompletedTrip(assignedAt)
        }
        log.info("backfilled {} completed trips over {}h", trips, hours)
    }

    private fun seedLiveTrips(count: Int) {
        val now = Instant.now()
        repeat(count) {
            startTrip(now.minusSeconds(rnd.nextLong(60, 900)), live = true)
        }
    }

    private fun startTrip(assignedAt: Instant, live: Boolean): ActiveTrip {
        val warehouse = Catalog.warehouses[rnd.nextInt(Catalog.warehouses.size)]
        val drop = Catalog.randomDrop(rnd)
        val scenario = when (rnd.nextInt(10)) {
            0, 1 -> TripScenario.LATE
            2 -> TripScenario.LONG_DWELL
            else -> TripScenario.NORMAL
        }
        val baseMinutes = 25 + rnd.nextInt(20)
        val promisedEta = when (scenario) {
            TripScenario.LATE -> assignedAt.plusSeconds((baseMinutes - 8).toLong() * 60)
            else -> assignedAt.plusSeconds(baseMinutes.toLong() * 60)
        }
        val trip = ActiveTrip(
            tripId = newId("trip"),
            courierId = "courier-${seq.getAndIncrement() % 120 + 1}",
            warehouse = warehouse,
            drop = drop,
            scenario = scenario,
            promisedEta = promisedEta,
            assignedAt = assignedAt,
            phase = TripPhase.EN_ROUTE_PICKUP,
            phaseStartedAt = assignedAt,
            position = Catalog.randomStartNear(warehouse, rnd),
        )
        publishEvent(trip, "ASSIGNED", assignedAt, trip.position)
        publishEvent(trip, "EN_ROUTE_PICKUP", assignedAt.plusSeconds(5), trip.position)
        if (live) {
            active[trip.tripId] = trip
        }
        return trip
    }

    private fun replayCompletedTrip(assignedAt: Instant) {
        val trip = startTrip(assignedAt, live = false)
        val pickupSec = 180L + rnd.nextLong(240)
        val dwellSec = when (trip.scenario) {
            TripScenario.LONG_DWELL -> 700L + rnd.nextLong(400)
            else -> 120L + rnd.nextLong(180)
        }
        val dropSec = when (trip.scenario) {
            TripScenario.LATE -> 1_200L + rnd.nextLong(600)
            else -> 480L + rnd.nextLong(420)
        }

        var t = assignedAt.plusSeconds(pickupSec / 2)
        emitPath(trip, trip.position, trip.warehouse.center, t, pickupSec / 2, steps = 6)
        trip.position = trip.warehouse.center
        t = assignedAt.plusSeconds(pickupSec)
        trip.phase = TripPhase.AT_WAREHOUSE
        trip.atWarehouseAt = t
        publishEvent(trip, "GEOFENCE_ENTER", t, trip.position)
        publishEvent(trip, "AT_WAREHOUSE", t, trip.position)

        t = t.plusSeconds(dwellSec)
        publishEvent(trip, "GEOFENCE_EXIT", t, trip.position)
        publishEvent(trip, "EN_ROUTE_DROP", t, trip.position)
        trip.phase = TripPhase.EN_ROUTE_DROP

        emitPath(trip, trip.warehouse.center, trip.drop, t, dropSec, steps = 10)
        t = t.plusSeconds(dropSec)
        trip.position = trip.drop
        trip.phase = TripPhase.DELIVERED
        publishEvent(trip, "DELIVERED", t, trip.position)
    }

    private fun advance(trip: ActiveTrip, now: Instant) {
        val elapsed = Duration.between(trip.phaseStartedAt, now).seconds
        when (trip.phase) {
            TripPhase.EN_ROUTE_PICKUP -> {
                val duration = 240.0
                trip.progress = (elapsed / duration).coerceIn(0.0, 1.0)
                trip.position = lerp(fixedStart(trip), trip.warehouse.center, trip.progress)
                if (trip.progress >= 1.0) {
                    trip.phase = TripPhase.AT_WAREHOUSE
                    trip.phaseStartedAt = now
                    trip.atWarehouseAt = now
                    trip.position = trip.warehouse.center
                    publishEvent(trip, "GEOFENCE_ENTER", now, trip.position)
                    publishEvent(trip, "AT_WAREHOUSE", now, trip.position)
                }
            }
            TripPhase.AT_WAREHOUSE -> {
                trip.position = trip.warehouse.center
                val dwellTarget = when (trip.scenario) {
                    TripScenario.LONG_DWELL -> 720L
                    else -> 150L + (trip.tripId.hashCode().toLong() and 120)
                }
                if (elapsed >= dwellTarget) {
                    trip.phase = TripPhase.EN_ROUTE_DROP
                    trip.phaseStartedAt = now
                    trip.progress = 0.0
                    publishEvent(trip, "GEOFENCE_EXIT", now, trip.position)
                    publishEvent(trip, "EN_ROUTE_DROP", now, trip.position)
                }
            }
            TripPhase.EN_ROUTE_DROP -> {
                val duration = when (trip.scenario) {
                    TripScenario.LATE -> 1_500.0
                    else -> 600.0
                }
                trip.progress = (elapsed / duration).coerceIn(0.0, 1.0)
                trip.position = lerp(trip.warehouse.center, trip.drop, trip.progress)
                if (trip.progress >= 1.0) {
                    trip.phase = TripPhase.DELIVERED
                    trip.phaseStartedAt = now
                    trip.position = trip.drop
                    publishEvent(trip, "DELIVERED", now, trip.position)
                }
            }
            TripPhase.DELIVERED -> Unit
        }
    }

    private fun fixedStart(trip: ActiveTrip): LatLon {
        val local = Random(trip.tripId.hashCode())
        return Catalog.randomStartNear(trip.warehouse, local)
    }

    private fun emitPath(
        trip: ActiveTrip,
        from: LatLon,
        to: LatLon,
        start: Instant,
        durationSec: Long,
        steps: Int,
    ) {
        repeat(steps) { i ->
            val t = (i + 1).toDouble() / steps
            val at = start.plusSeconds((durationSec * t).toLong())
            trip.position = lerp(from, to, t)
            publishPing(trip, at)
        }
    }

    private fun publishPing(trip: ActiveTrip, at: Instant) {
        val point = noisy(trip.position, rnd)
        val speed = when (trip.phase) {
            TripPhase.AT_WAREHOUSE -> 0.0
            TripPhase.DELIVERED -> 0.0
            else -> 18.0 + rnd.nextDouble() * 12.0
        }
        val ping = GpsPing(
            pingId = newId("ping"),
            tripId = trip.tripId,
            courierId = trip.courierId,
            warehouseId = trip.warehouse.id,
            lat = point.lat,
            lon = point.lon,
            speedKmh = speed,
            ts = at.asEpochSeconds(),
        )
        kafkaTemplate.send(properties.gpsTopic, trip.tripId, ping).get(5, TimeUnit.SECONDS)
    }

    private fun publishEvent(trip: ActiveTrip, type: String, at: Instant, pos: LatLon) {
        val event = TripEvent(
            eventId = newId("evt"),
            tripId = trip.tripId,
            courierId = trip.courierId,
            warehouseId = trip.warehouse.id,
            eventType = type,
            promisedEta = trip.promisedEta.asEpochSeconds(),
            lat = pos.lat,
            lon = pos.lon,
            ts = at.asEpochSeconds(),
        )
        kafkaTemplate.send(properties.eventsTopic, trip.tripId, event).get(5, TimeUnit.SECONDS)
    }
}
