package com.example.streams.serde

data class SensorReading(
  var deviceId: String,
  var temperature: Double,
  var humidity: Double,
  var pressure: Double,
  var timestamp: String
)

fun fromDebeziumEvent(payload: com.example.streams.Payload): SensorReading? {
    val sensorData = payload.after ?: return null
    val deviceId = sensorData.device_id ?: return null
    val temperature = sensorData.temperature ?: return null
    val humidity = sensorData.humidity ?: return null
    val pressure = sensorData.pressure ?: return null
    val timestamp = sensorData.timestamp ?: ""
    
    return SensorReading(
        deviceId = deviceId,
        temperature = temperature,
        humidity = humidity,
        pressure = pressure,
        timestamp = timestamp
    )
}
