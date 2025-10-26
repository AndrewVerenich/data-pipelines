package com.example.iot.entity

import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.Instant

@Entity
@Table(name = "sensor_data")
data class SensorReading(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long? = null,

    var deviceId: String,
    var temperature: Double,
    var humidity: Double,
    var pressure: Double,
    var timestamp: Instant
)