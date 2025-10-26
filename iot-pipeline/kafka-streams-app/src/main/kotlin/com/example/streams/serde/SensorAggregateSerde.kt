package com.example.streams.serde

import org.apache.kafka.common.serialization.Serdes

class SensorAggregateSerde : Serdes.WrapperSerde<SensorAggregate>(
  JsonSerializer(),
  JsonDeserializer(SensorAggregate::class.java)
)
