package com.example.streams.serde

class SensorAggregate(
  private var sumTemp: Double = 0.0,
  private var sumHum: Double = 0.0,
  private var sumPres: Double = 0.0,
  private var count: Long = 0
) {
  fun add(r: SensorReading): SensorAggregate {
    sumTemp += r.temperature
    sumHum += r.humidity
    sumPres += r.pressure
    count++
    return this
  }

  fun avgTemp(): Double = sumTemp / count
  fun avgHum(): Double = sumHum / count
  fun avgPres(): Double = sumPres / count
  fun getCount(): Long = count
  fun getSumTemp(): Double = sumTemp
  fun getSumHum(): Double = sumHum
  fun getSumPres(): Double = sumPres
}
