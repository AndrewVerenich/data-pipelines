package com.example.iot

import org.springframework.boot.SpringApplication
import org.springframework.boot.autoconfigure.SpringBootApplication

@SpringBootApplication
class IotDataGeneratorApplication

fun main(args: Array<String>) {
    SpringApplication.run(IotDataGeneratorApplication::class.java, *args)
}
