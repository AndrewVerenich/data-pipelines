package com.example.logistics.simulator

import com.fasterxml.jackson.annotation.JsonProperty
import java.time.Instant
import java.util.UUID
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random

data class LatLon(val lat: Double, val lon: Double)

data class Warehouse(
    val id: String,
    val name: String,
    val center: LatLon,
    val radiusM: Double = 200.0,
)

enum class TripPhase {
    EN_ROUTE_PICKUP,
    AT_WAREHOUSE,
    EN_ROUTE_DROP,
    DELIVERED,
}

enum class TripScenario {
    NORMAL,
    LATE,
    LONG_DWELL,
}

data class GpsPing(
    @JsonProperty("ping_id") val pingId: String,
    @JsonProperty("trip_id") val tripId: String,
    @JsonProperty("courier_id") val courierId: String,
    @JsonProperty("warehouse_id") val warehouseId: String,
    val lat: Double,
    val lon: Double,
    @JsonProperty("speed_kmh") val speedKmh: Double,
    val ts: Double,
)

data class TripEvent(
    @JsonProperty("event_id") val eventId: String,
    @JsonProperty("trip_id") val tripId: String,
    @JsonProperty("courier_id") val courierId: String,
    @JsonProperty("warehouse_id") val warehouseId: String,
    @JsonProperty("event_type") val eventType: String,
    @JsonProperty("promised_eta") val promisedEta: Double,
    val lat: Double,
    val lon: Double,
    val ts: Double,
)

data class ActiveTrip(
    val tripId: String,
    val courierId: String,
    val warehouse: Warehouse,
    val drop: LatLon,
    val scenario: TripScenario,
    val promisedEta: Instant,
    val assignedAt: Instant,
    var phase: TripPhase,
    var phaseStartedAt: Instant,
    var atWarehouseAt: Instant? = null,
    var position: LatLon,
    var progress: Double = 0.0,
)

object Catalog {
    val warehouses = listOf(
        Warehouse("wh-center", "Склад Центр", LatLon(53.9023, 27.5619)),
        Warehouse("wh-east", "Склад Восток", LatLon(53.9080, 27.6200)),
        Warehouse("wh-west", "Склад Запад", LatLon(53.8950, 27.5000)),
        Warehouse("wh-south", "Склад Юг", LatLon(53.8600, 27.5500)),
    )

    fun randomDrop(rnd: Random): LatLon {
        val lat = 53.85 + rnd.nextDouble() * 0.08
        val lon = 27.48 + rnd.nextDouble() * 0.16
        return LatLon(lat, lon)
    }

    fun randomStartNear(warehouse: Warehouse, rnd: Random): LatLon {
        val bearing = rnd.nextDouble() * Math.PI * 2
        val distKm = 0.8 + rnd.nextDouble() * 2.5
        return offset(warehouse.center, distKm, bearing)
    }
}

fun offset(origin: LatLon, distanceKm: Double, bearingRad: Double): LatLon {
    val dLat = (distanceKm / 111.0) * cos(bearingRad)
    val dLon = (distanceKm / (111.0 * cos(Math.toRadians(origin.lat)))) * sin(bearingRad)
    return LatLon(origin.lat + dLat, origin.lon + dLon)
}

fun lerp(a: LatLon, b: LatLon, t: Double): LatLon {
    val clamped = t.coerceIn(0.0, 1.0)
    return LatLon(
        a.lat + (b.lat - a.lat) * clamped,
        a.lon + (b.lon - a.lon) * clamped,
    )
}

fun distanceKm(a: LatLon, b: LatLon): Double {
    val dLat = (a.lat - b.lat) * 111.0
    val dLon = (a.lon - b.lon) * 111.0 * cos(Math.toRadians((a.lat + b.lat) / 2))
    return sqrt(dLat * dLat + dLon * dLon)
}

fun noisy(point: LatLon, rnd: Random, meters: Double = 12.0): LatLon {
    val dLat = (rnd.nextDouble() - 0.5) * 2 * meters / 111_000.0
    val dLon = (rnd.nextDouble() - 0.5) * 2 * meters / (111_000.0 * cos(Math.toRadians(point.lat)))
    return LatLon(point.lat + dLat, point.lon + dLon)
}

fun newId(prefix: String): String = "$prefix-${UUID.randomUUID().toString().take(8)}"

fun Instant.asEpochSeconds(): Double = epochSecond + nano / 1_000_000_000.0
