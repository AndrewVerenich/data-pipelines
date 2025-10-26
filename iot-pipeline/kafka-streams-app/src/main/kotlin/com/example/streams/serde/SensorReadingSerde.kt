package com.example.streams.serde

import com.google.gson.Gson
import org.apache.kafka.common.serialization.Deserializer
import org.apache.kafka.common.serialization.Serdes
import org.apache.kafka.common.serialization.Serializer

class SensorReadingSerde : Serdes.WrapperSerde<SensorReading>(
    SensorReadingSerializer(),
    SensorReadingDeserializer()
)

class SensorReadingSerializer : Serializer<SensorReading> {
    private val gson = Gson()
    
    override fun serialize(topic: String?, data: SensorReading?): ByteArray? {
        return data?.let { gson.toJson(it).toByteArray() }
    }
}

class SensorReadingDeserializer : Deserializer<SensorReading> {
    private val gson = Gson()
    
    override fun deserialize(topic: String?, data: ByteArray?): SensorReading? {
        return data?.let { gson.fromJson(String(it), SensorReading::class.java) }
    }
} 