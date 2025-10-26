package com.example.iot.repository

import com.example.iot.entity.SensorReading
import org.springframework.data.jpa.repository.JpaRepository

interface SensorRepository : JpaRepository<SensorReading, Long>